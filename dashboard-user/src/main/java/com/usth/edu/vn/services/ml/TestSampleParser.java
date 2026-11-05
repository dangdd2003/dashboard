package com.usth.edu.vn.services.ml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Strict parser for an uploaded test sample: numbers separated by commas and/or
 * whitespace (e.g. {@code 1.25, 3e-2, -4}). Anything else is rejected with 400.
 */
public final class TestSampleParser {

  public static final int MAX_VALUES = 10_000;
  public static final long MAX_BYTES = 1024L * 1024L;

  private static final Pattern SEPARATOR = Pattern.compile("[,\\s]+");
  private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

  private TestSampleParser() {
  }

  public static List<Double> parse(FileUpload upload) {
    if (upload == null || upload.uploadedFile() == null) {
      throw HttpErrors.badRequest("Missing multipart file 'test_sample'");
    }
    if (upload.size() > MAX_BYTES) {
      throw HttpErrors.badRequest("'test_sample' is too large (max " + MAX_BYTES + " bytes)");
    }
    String text;
    try {
      text = Files.readString(upload.uploadedFile(), StandardCharsets.UTF_8);
    } catch (CharacterCodingException e) {
      throw HttpErrors.badRequest("'test_sample' must be UTF-8 text");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read uploaded test sample", e);
    }
    return parse(text);
  }

  public static List<Double> parse(String text) {
    if (text == null) {
      throw HttpErrors.badRequest("'test_sample' is empty");
    }
    String trimmed = text.startsWith("﻿") ? text.substring(1) : text;
    trimmed = trimmed.strip();
    if (trimmed.isEmpty()) {
      throw HttpErrors.badRequest("'test_sample' is empty");
    }
    String[] tokens = SEPARATOR.split(trimmed);
    if (tokens.length > MAX_VALUES) {
      throw HttpErrors.badRequest("'test_sample' has too many values (max " + MAX_VALUES + ")");
    }
    List<Double> values = new ArrayList<>(tokens.length);
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      if (!NUMBER.matcher(token).matches()) {
        throw HttpErrors.badRequest("'test_sample' value #" + (i + 1) + " is not a number: '" + abbreviate(token) + "'");
      }
      double value = Double.parseDouble(token);
      if (!Double.isFinite(value)) {
        throw HttpErrors.badRequest("'test_sample' value #" + (i + 1) + " is out of range");
      }
      values.add(value);
    }
    return values;
  }

  private static String abbreviate(String token) {
    return token.length() <= 32 ? token : token.substring(0, 32) + "...";
  }
}
