package com.usth.edu.vn.services;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usth.edu.vn.model.Models;
import com.usth.edu.vn.repository.InferenceRepository;
import com.usth.edu.vn.repository.ModelRepository;
import com.usth.edu.vn.services.ml.HttpErrors;
import com.usth.edu.vn.services.ml.InferenceRequest;
import com.usth.edu.vn.services.ml.PredictionResponse;
import com.usth.edu.vn.services.ml.PythonModelClient;
import com.usth.edu.vn.services.ml.TestSampleParser;
import com.usth.edu.vn.services.ml.TrainingResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

/**
 * Calls the Python ML service and records the outcome.
 *
 * Deliberately NOT transactional: the remote call runs without holding a DB
 * connection/transaction, and the result is persisted afterwards in a short
 * transaction ({@link InferenceRepository#recordInference},
 * {@link InferenceRepository#recordTrainingResult}).
 */
@ApplicationScoped
public class InferenceService {

  private static final Logger LOG = Logger.getLogger(InferenceService.class);

  static final Pattern MODEL_TYPE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
  static final int MAX_NDIM = 10_000;
  /** Stays below MySQL TEXT (65,535 bytes) for ASCII JSON. */
  static final int MAX_RESULT_CHARS = 60_000;
  /** Length of the {@code resources.filepath} column. */
  static final int MAX_FILEPATH_CHARS = 255;

  @Inject
  @RestClient
  PythonModelClient pythonClient;

  @Inject
  ModelRepository modelRepository;

  @Inject
  InferenceRepository inferenceRepository;

  @Inject
  ObjectMapper objectMapper;

  /** Train + evaluate {@code modelId} for the caller; stores the produced params file as a resource. */
  public TrainingResponse trainEvalMPAP(long callerId, long modelId, int pcaDim) {
    validateNdim(pcaDim);
    ModelTarget target = resolveModel(modelId);

    TrainingResponse training = callUpstream("Training",
        () -> pythonClient.run(target.owner(), target.type(), target.name(), pcaDim, callerId));

    if (training == null || training.result() == null || training.result().isBlank()) {
      throw HttpErrors.status(502, "ML service returned no training result");
    }
    if (training.result().length() > MAX_FILEPATH_CHARS) {
      throw HttpErrors.status(502, "ML service returned an invalid training result");
    }
    inferenceRepository.recordTrainingResult(callerId, training.result());
    return training;
  }

  /** Run an inference of {@code modelId} on the uploaded test sample; stores the raw result. */
  public PredictionResponse inferenceMPAP(long callerId, long modelId, Integer pcaDim, FileUpload sample) {
    if (pcaDim != null) {
      validateNdim(pcaDim);
    }
    List<Double> input = TestSampleParser.parse(sample);
    ModelTarget target = resolveModel(modelId);

    JsonNode body = callUpstream("Inference",
        () -> pythonClient.inference(target.owner(), target.type(), target.name(), pcaDim, callerId,
            new InferenceRequest(input)));

    if (body == null || !body.isObject()) {
      throw HttpErrors.status(502, "ML service returned an invalid inference response");
    }
    PredictionResponse prediction;
    try {
      prediction = objectMapper.treeToValue(body, PredictionResponse.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      LOG.warnf("Inference: could not map ML service response: %s", e.getMessage());
      throw HttpErrors.status(502, "ML service returned an invalid inference response");
    }

    String stored = body.toString();
    if (stored.length() > MAX_RESULT_CHARS) {
      try {
        stored = objectMapper.writeValueAsString(prediction);
      } catch (JsonProcessingException e) {
        throw HttpErrors.status(502, "ML service returned an invalid inference response");
      }
      if (stored.length() > MAX_RESULT_CHARS) {
        throw HttpErrors.status(502, "ML service returned an oversized inference response");
      }
    }
    inferenceRepository.recordInference(callerId, modelId, stored);
    return prediction;
  }

  private static void validateNdim(int ndim) {
    if (ndim < 0 || ndim > MAX_NDIM) {
      throw HttpErrors.badRequest("pca_dim must be between 0 and " + MAX_NDIM);
    }
  }

  /** Loads the model (404 if missing) and derives validated path segments for the ML service. */
  private ModelTarget resolveModel(long modelId) {
    Models model = modelRepository.findByIdOptional(modelId)
        .orElseThrow(() -> HttpErrors.notFound("Model not found"));

    if (model.getUser() == null || model.getUser().getId() == null) {
      throw HttpErrors.status(409, "Model has no owner");
    }
    String type = model.getType();
    if (type == null || !MODEL_TYPE.matcher(type).matches()) {
      throw HttpErrors.status(409, "Model has an invalid type");
    }
    String name = modelName(model.getFilepath());
    if (name == null || !MODEL_NAME.matcher(name).matches()) {
      throw HttpErrors.status(409, "Model has an invalid file name");
    }
    return new ModelTarget(model.getUser().getId(), type, name);
  }

  /** File name of the stored model without directories and without its extension. */
  static String modelName(String filepath) {
    if (filepath == null || filepath.isBlank()) {
      return null;
    }
    int slash = Math.max(filepath.lastIndexOf('/'), filepath.lastIndexOf('\\'));
    String fileName = filepath.substring(slash + 1);
    return fileName.replaceFirst("[.][^.]+$", "");
  }

  /** Invokes the ML service and maps transport/upstream failures to 502/503/504. */
  private <T> T callUpstream(String operation, Supplier<T> call) {
    try {
      return call.get();
    } catch (WebApplicationException e) {
      int status = e.getResponse() != null ? e.getResponse().getStatus() : -1;
      LOG.warnf("%s: ML service returned HTTP %d%s", operation, status, upstreamBody(e));
      throw HttpErrors.status(502, operation + " failed: ML service returned HTTP " + status);
    } catch (ProcessingException e) {
      if (hasCause(e, true)) {
        LOG.warnf("%s: ML service timed out: %s", operation, e.getMessage());
        throw HttpErrors.status(504, operation + " failed: ML service timed out");
      }
      if (hasCause(e, false)) {
        LOG.warnf("%s: ML service unreachable: %s", operation, e.getMessage());
        throw HttpErrors.status(503, operation + " failed: ML service is unavailable");
      }
      LOG.warnf(e, "%s: ML service call failed", operation);
      throw HttpErrors.status(502, operation + " failed: invalid response from ML service");
    } catch (RuntimeException e) {
      LOG.warnf(e, "%s: ML service call failed", operation);
      throw HttpErrors.status(502, operation + " failed: invalid response from ML service");
    }
  }

  /** {@code timeout=true}: any timeout in the cause chain; otherwise a connect/resolve failure. */
  private static boolean hasCause(Throwable error, boolean timeout) {
    Throwable t = error;
    for (int depth = 0; t != null && depth < 16; depth++, t = t.getCause()) {
      if (timeout) {
        if (t instanceof java.util.concurrent.TimeoutException
            || t instanceof java.net.SocketTimeoutException
            || t.getClass().getSimpleName().contains("Timeout")) {
          return true;
        }
      } else if (t instanceof java.net.ConnectException
          || t instanceof java.net.UnknownHostException
          || t instanceof java.nio.channels.UnresolvedAddressException) {
        return true;
      }
    }
    return false;
  }

  private static String upstreamBody(WebApplicationException e) {
    try {
      if (e.getResponse() != null && e.getResponse().hasEntity()) {
        String body = e.getResponse().readEntity(String.class);
        if (body != null && !body.isEmpty()) {
          return ": " + (body.length() > 500 ? body.substring(0, 500) + "..." : body);
        }
      }
    } catch (RuntimeException ignored) {
      // entity already consumed or unreadable
    }
    return "";
  }

  private record ModelTarget(long owner, String type, String name) {
  }
}
