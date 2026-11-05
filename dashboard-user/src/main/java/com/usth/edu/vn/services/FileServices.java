package com.usth.edu.vn.services;

import static com.usth.edu.vn.services.FileName.AVATARS;
import static com.usth.edu.vn.services.FileName.MODELS;
import static com.usth.edu.vn.services.FileName.RESOURCES;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;

/**
 * All file-system access for user data goes through this class.
 *
 * <p>Layout: {@code <root>/<userId>/<category>[/<type>]/<uuid>.<ext>}. Only paths
 * <em>relative</em> to the root are ever stored in the database or returned to
 * clients; every read/delete re-resolves them against the root and rejects
 * anything that escapes it.
 */
@Singleton
public class FileServices {

  private static final Logger LOG = Logger.getLogger(FileServices.class);

  /** Allowed values for the {@code type} sub-directory. */
  public static final Pattern TYPE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  private static final Pattern EXTENSION_PATTERN = Pattern.compile("[A-Za-z0-9]{1,10}");

  private static final Set<String> CATEGORIES = Set.of(MODELS, RESOURCES, AVATARS);

  /** Maximum avatar size in bytes (5 MiB). */
  public static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

  private static final byte[] PNG_MAGIC = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
  private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

  public static final String IMAGE_PNG = "image/png";
  public static final String IMAGE_JPEG = "image/jpeg";

  @ConfigProperty(name = "system.folder", defaultValue = "GroupProject")
  String defaultFolder;

  @Inject
  TransactionSynchronizationRegistry txRegistry;

  private Path root;

  @PostConstruct
  void init() {
    root = Path.of(defaultFolder).toAbsolutePath().normalize();
  }

  public Path root() {
    return root;
  }

  public static boolean isValidType(String type) {
    return type != null && TYPE_PATTERN.matcher(type).matches();
  }

  // ---------------------------------------------------------------------------
  // Upload
  // ---------------------------------------------------------------------------

  /**
   * Stores an uploaded file under {@code root/userId/category[/type]/<uuid>.<ext>}.
   *
   * @param category one of {@link FileName#MODELS}, {@link FileName#RESOURCES}, {@link FileName#AVATARS}
   * @param type     sub-directory, may be {@code null}; otherwise must match {@link #TYPE_PATTERN}
   * @return the stored path relative to the root, using forward slashes
   */
  public String uploadFile(long userId, FileUpload upload, String category, String type) throws IOException {
    if (upload == null || upload.uploadedFile() == null) {
      throw new BadRequestException("No file uploaded");
    }
    String ext = extensionOf(upload.fileName());
    // Models keep their (validated) original base name: the ML service locates a model by
    // /model/{owner}/{type}/{name}. Other uploads get an unguessable random name.
    String baseName = FileName.MODELS.equals(category)
        ? modelBaseName(upload.fileName())
        : UUID.randomUUID().toString();
    return store(userId, upload.uploadedFile(), category, type, baseName + (ext == null ? "" : "." + ext));
  }

  private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

  /** Original model file name without directory or extension; 400 unless it is a safe identifier. */
  static String modelBaseName(String fileName) {
    if (fileName == null) {
      throw new BadRequestException("Model file name is required");
    }
    String base = fileName.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1);
    int dot = base.lastIndexOf('.');
    if (dot > 0) {
      base = base.substring(0, dot);
    }
    if (!MODEL_NAME_PATTERN.matcher(base).matches()) {
      throw new BadRequestException("Invalid model file name: must match " + MODEL_NAME_PATTERN.pattern());
    }
    return base;
  }

  private String store(long userId, Path source, String category, String type, String name)
      throws IOException {
    if (!CATEGORIES.contains(category)) {
      throw new IllegalArgumentException("Unknown file category: " + category);
    }
    if (type != null && !isValidType(type)) {
      throw new BadRequestException("Invalid type: must match " + TYPE_PATTERN.pattern());
    }
    Path dir = root.resolve(Long.toString(userId)).resolve(category);
    if (type != null) {
      dir = dir.resolve(type);
    }
    Path target = dir.resolve(name).normalize();
    if (!target.startsWith(root)) {
      throw new ForbiddenException("Invalid file location");
    }
    Files.createDirectories(target.getParent());
    try {
      // No REPLACE_EXISTING: never overwrite an existing file.
      Files.copy(source, target);
    } catch (java.nio.file.FileAlreadyExistsException e) {
      throw new jakarta.ws.rs.ClientErrorException("A file with this name already exists", 409);
    }
    return toRelative(target);
  }

  /** Sanitized extension of the original file name, or {@code null} if none / not allowed. */
  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return null;
    }
    // strip any client-side directory component
    String base = fileName.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1);
    int dot = base.lastIndexOf('.');
    if (dot < 0 || dot == base.length() - 1) {
      return null;
    }
    String ext = base.substring(dot + 1);
    return EXTENSION_PATTERN.matcher(ext).matches() ? ext.toLowerCase(Locale.ROOT) : null;
  }

  private String toRelative(Path absolute) {
    return root.relativize(absolute).toString().replace(absolute.getFileSystem().getSeparator(), "/");
  }

  // ---------------------------------------------------------------------------
  // Resolve / delete
  // ---------------------------------------------------------------------------

  /**
   * Resolves a stored relative path against the root.
   *
   * @throws ForbiddenException if the path is null, absolute or escapes the root
   */
  public Path resolveStored(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      throw new ForbiddenException("Invalid file path");
    }
    Path rel;
    try {
      rel = Path.of(relativePath);
    } catch (InvalidPathException e) {
      throw new ForbiddenException("Invalid file path");
    }
    if (rel.isAbsolute() || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
      throw new ForbiddenException("Invalid file path");
    }
    Path resolved = root.resolve(rel).normalize();
    if (!resolved.startsWith(root) || resolved.equals(root)) {
      throw new ForbiddenException("Invalid file path");
    }
    return resolved;
  }

  /**
   * Deletes the single stored file after the current JTA transaction commits
   * (immediately if there is no transaction). Never recursive.
   */
  public void deleteStoredFileAfterCommit(String relativePath) {
    Path file = resolveStored(relativePath);
    runAfterCommit(() -> deleteSingleFile(file));
  }

  /**
   * Deletes the single stored file if the current JTA transaction rolls back
   * (used to avoid orphan files when the DB insert fails). No-op without a transaction.
   */
  public void deleteStoredFileOnRollback(String relativePath) {
    Path file = resolveStored(relativePath);
    if (!hasTransaction()) {
      return;
    }
    txRegistry.registerInterposedSynchronization(new Synchronization() {
      @Override
      public void beforeCompletion() {
      }

      @Override
      public void afterCompletion(int status) {
        if (status != Status.STATUS_COMMITTED) {
          deleteSingleFile(file);
        }
      }
    });
  }

  public void createUserDir(long userId) {
    Path userDir = userDir(userId);
    try {
      Files.createDirectories(userDir.resolve(MODELS));
      Files.createDirectories(userDir.resolve(RESOURCES));
      Files.createDirectories(userDir.resolve(AVATARS));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create user directory", e);
    }
  }

  /** Recursively deletes {@code root/userId} (and nothing else) after the transaction commits. */
  public void deleteUserDirAfterCommit(long userId) {
    Path userDir = userDir(userId);
    runAfterCommit(() -> deleteRecursively(userDir));
  }

  private Path userDir(long userId) {
    Path userDir = root.resolve(Long.toString(userId)).normalize();
    if (!userDir.startsWith(root) || userDir.equals(root)) {
      throw new ForbiddenException("Invalid user directory");
    }
    return userDir;
  }

  private boolean hasTransaction() {
    try {
      int status = txRegistry.getTransactionStatus();
      return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private void runAfterCommit(Runnable action) {
    if (!hasTransaction()) {
      action.run();
      return;
    }
    txRegistry.registerInterposedSynchronization(new Synchronization() {
      @Override
      public void beforeCompletion() {
      }

      @Override
      public void afterCompletion(int status) {
        if (status == Status.STATUS_COMMITTED) {
          action.run();
        }
      }
    });
  }

  private void deleteSingleFile(Path file) {
    try {
      if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
        LOG.warnf("Refusing to delete directory %s as a stored file", file);
        return;
      }
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.warnf(e, "Could not delete file %s", file);
    }
  }

  private void deleteRecursively(Path dir) {
    if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    // Files.walk does not follow symbolic links, so links are deleted, not their targets.
    try (Stream<Path> walk = Files.walk(dir)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path p : paths) {
        if (!p.normalize().startsWith(dir)) {
          continue;
        }
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          LOG.warnf(e, "Could not delete %s", p);
        }
      }
    } catch (IOException e) {
      LOG.warnf(e, "Could not delete directory %s", dir);
    }
  }

  // ---------------------------------------------------------------------------
  // Avatars
  // ---------------------------------------------------------------------------

  /**
   * Validates that the upload is a PNG or JPEG image (declared content type and
   * magic bytes), stores it and removes the user's previous avatar(s).
   *
   * @return the stored path relative to the root
   */
  public String uploadAvatar(long userId, FileUpload upload) throws IOException {
    if (upload == null || upload.uploadedFile() == null) {
      throw new BadRequestException("No image uploaded (form field 'image')");
    }
    if (upload.size() > MAX_AVATAR_SIZE) {
      throw new BadRequestException("Avatar too large (max " + MAX_AVATAR_SIZE + " bytes)");
    }
    String declared = upload.contentType() == null ? ""
        : upload.contentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
    String detected = detectImageType(upload.uploadedFile());
    boolean declaredOk = declared.equals(IMAGE_PNG) || declared.equals(IMAGE_JPEG) || declared.equals("image/jpg");
    if (detected == null || !declaredOk) {
      throw new BadRequestException("Avatar must be a PNG or JPEG image");
    }
    String extension = IMAGE_PNG.equals(detected) ? "png" : "jpg";
    String stored = store(userId, upload.uploadedFile(), AVATARS, null, extension);
    Path keep = resolveStored(stored);
    deleteAvatarFiles(userId, keep);
    return stored;
  }

  /** The user's current avatar (most recently written), if any. */
  public Optional<Path> findAvatar(long userId) throws IOException {
    Path dir = userDir(userId).resolve(AVATARS);
    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
          .filter(p -> p.normalize().startsWith(root))
          .max(Comparator.comparing(FileServices::lastModified));
    }
  }

  /** Deletes all of the user's avatar files. */
  public void deleteAvatar(long userId) throws IOException {
    deleteAvatarFiles(userId, null);
  }

  private void deleteAvatarFiles(long userId, Path keep) throws IOException {
    Path dir = userDir(userId).resolve(AVATARS);
    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    List<Path> toDelete;
    try (Stream<Path> files = Files.list(dir)) {
      toDelete = files
          .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(p))
          .filter(p -> p.normalize().startsWith(root))
          .filter(p -> keep == null || !p.normalize().equals(keep))
          .toList();
    }
    for (Path p : toDelete) {
      deleteSingleFile(p);
    }
  }

  /** Returns {@code image/png} or {@code image/jpeg} based on magic bytes, or {@code null}. */
  public static String detectImageType(Path file) throws IOException {
    byte[] header = new byte[PNG_MAGIC.length];
    int read;
    try (InputStream in = Files.newInputStream(file)) {
      read = in.readNBytes(header, 0, header.length);
    }
    if (read >= PNG_MAGIC.length && Arrays.equals(header, 0, PNG_MAGIC.length, PNG_MAGIC, 0, PNG_MAGIC.length)) {
      return IMAGE_PNG;
    }
    if (read >= JPEG_MAGIC.length && Arrays.equals(header, 0, JPEG_MAGIC.length, JPEG_MAGIC, 0, JPEG_MAGIC.length)) {
      return IMAGE_JPEG;
    }
    return null;
  }

  private static FileTime lastModified(Path p) {
    try {
      return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS);
    } catch (IOException e) {
      return FileTime.fromMillis(0);
    }
  }
}
