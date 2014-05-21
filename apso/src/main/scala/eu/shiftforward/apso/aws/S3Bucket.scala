package eu.shiftforward.apso.aws

import com.amazonaws.{ AmazonClientException, AmazonServiceException }
import com.amazonaws.auth._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.typesafe.config.ConfigFactory
import eu.shiftforward.apso.Logging
import java.io.{ File, FileOutputStream }
import scala.collection.JavaConversions._
import scala.util.{ Failure, Success, Try }

/**
 * A representation of an Amazon's S3 bucket. This class wraps an
 * [[com.amazonaws.services.s3.AmazonS3Client]] and provides a higher level interface for pushing
 * and pulling files to and from a bucket.
 *
 * @param bucketName the name of the bucket
 * @param credentials optional AWS credentials to use. If the parameter is not supplied, they will
 *                    be retrieved from the [[eu.shiftforward.apso.aws.CredentialStore]].
 */
class S3Bucket(val bucketName: String,
               private val credentials: AWSCredentials = CredentialStore.getCredentials)
    extends Logging {

  private[this] lazy val config = ConfigFactory.load()

  private[this] lazy val configPrefix = "aws.s3"

  private[this] lazy val endpoint = Try(config.getString(configPrefix + ".endpoint")).getOrElse("s3.amazonaws.com")
  private[this] lazy val region = Try(config.getString(configPrefix + ".region")).getOrElse(null)

  private[this] lazy val s3 = {
    val client = new AmazonS3Client(credentials)
    client.setEndpoint(endpoint)
    if (!client.doesBucketExist(bucketName)) {
      client.createBucket(bucketName, Region.fromValue(region))
    }
    client
  }

  private[this] def splitKey(key: String) = {
    val iExtensionPoint = key.lastIndexOf('.')
    val iSlash = key.lastIndexOf('/')
    val mainKey = if (iSlash != -1) key.substring(0, iSlash) else ""
    val extension = if (iExtensionPoint != -1) key.substring(iExtensionPoint) else ""
    val name = if (iExtensionPoint != -1) key.substring(0, iExtensionPoint) else key

    (mainKey, name, extension)
  }

  private[this] def sanitizeKey(key: String) = if (key.startsWith("./")) key.drop(2) else key

  /**
   * Returns a list of filenames in a bucket matching a given prefix.
   * @param key the prefix to match
   * @return a list of filenames in a bukcet matching a given prefix.
   */
  def getFilesWithMatchingPrefix(key: String) = {
    log.info("Finding files matching prefix '{}'...", key)
    val (mainKey, name, extension) = splitKey(sanitizeKey(key))

    var request = new ListObjectsRequest().withBucketName(bucketName)
    if (mainKey != "") request = request.withPrefix(mainKey)

    var objectListing = s3.listObjects(request)

    var summaries = objectListing.getObjectSummaries.toList

    while (objectListing.isTruncated) {
      objectListing = s3.listNextBatchOfObjects(objectListing)
      summaries :::= objectListing.getObjectSummaries.toList
      log.info("Asking for another batch of objects...")
    }

    val files = summaries.foldLeft(List[String]()) {
      (files, summary) =>
        val (_, summaryName, _) = splitKey(summary.getKey)
        if (!summaryName.endsWith("/") &&
          (name.startsWith(summaryName) || summaryName.startsWith(name)))
          summary.getKey :: files
        else
          files
    }

    log.info("Found {} matching objects...", files.size)
    files
  }

  /**
   * Pushes a given local `File` to the location specified by `key` in the bucket.
   * @param key the remote pathname for the file
   * @param file the local `File` to push
   * @return true if the push was successful, false otherwise.
   */
  def push(key: String, file: File): Boolean = handle {
    log.info("Pushing '{}' to 's3://{}/{}'", file.getPath, bucketName, key)
    s3.putObject(new PutObjectRequest(bucketName, sanitizeKey(key), file))
  }.isDefined

  /**
   * Sets an access control list on a given Amazon S3 object.
   * @param key the remote pathname for the file
   * @param acl the `CannedAccessControlList` to be applied to the Amazon S3 object
   */
  def setAcl(key: String, acl: CannedAccessControlList) {
    log.info("Setting 's3://{}/{}' permissions to '{}'", bucketName, key, acl)
    s3.setObjectAcl(bucketName, key, acl)
  }

  /**
   * Backups a remote file with the given `key`. A backup consists in copying the supplied file to a backup folder under
   * the same bucket and folder the file is currently in.
   * @param key the remote pathname to backup
   * @return true if the backup was successful, false otherwise.
   */
  def backup(key: String): Boolean = handle {
    val sanitizedKey = sanitizeKey(key)
    val (mainKey, name, extension) = splitKey(sanitizedKey)

    s3.copyObject(new CopyObjectRequest(
      bucketName,
      sanitizedKey,
      bucketName,
      mainKey + "/backup/" + name.substring(mainKey.size + 1) + extension))
  }.isDefined

  /**
   * Pulls a remote file with the given `key`, to the local storage in the pathname provided by `destination`.
   * @param key the remote pathname to pull from
   * @param destination the local pathname to pull to
   * @return true if the pull was successful, false otherwise
   */
  def pull(key: String, destination: String): Boolean = handle {
    log.info("Pulling 's3://{}/{}' to '{}'", bucketName, key, destination)

    val s3Object = s3.getObject(new GetObjectRequest(bucketName, sanitizeKey(key)))
    val inputStream = s3Object.getObjectContent

    val f = new File(destination).getCanonicalFile
    f.getParentFile.mkdirs()
    val outputStream = new FileOutputStream(f)

    var read = 0
    val bytes = new Array[Byte](1024)

    read = inputStream.read(bytes)
    while (read != -1) {
      outputStream.write(bytes, 0, read)
      read = inputStream.read(bytes)
    }

    inputStream.close()
    outputStream.flush()
    outputStream.close()
  }.isDefined

  private[this] def handler: PartialFunction[Throwable, Boolean] = {
    case ase: AmazonServiceException =>
      log.error("Caught an AmazonServiceException, which means your request made it to Amazon S3," +
        " but was rejected with an error response for some reason")
      log.error("Error Message:    {}", ase.getMessage)
      log.error("HTTP Status Code: {}", ase.getStatusCode)
      log.error("AWS Error Code:   {}", ase.getErrorCode)
      log.error("Error Type:       {}", ase.getErrorType)
      log.error("Request ID:       {}", ase.getRequestId)
      ase.getStatusCode == 404 // if the file doesn't exist there's no need to retry

    case ace: AmazonClientException =>
      log.error("Caught an AmazonClientException, which means the client encountered a serious " +
        "internal problem while trying to communicate with S3, such as not being able to access " +
        "the network")
      log.error("Error Message:    {}", ace.getMessage)
      false

    case e: Exception =>
      log.error("An error occurred", e)
      false
  }

  private[this] def handle[T](f: => T, tries: Int = 3, sleepTime: Int = 5000): Option[T] =
    if (tries == 0) { log.error("Max retries reached. Aborting S3 operation"); None }
    else Try(f) match {
      case Success(res) => Some(res)
      case Failure(e) if !handler(e) =>
        if (tries > 1) {
          log.warn("Error during S3 operation. Retrying in {}ms ({} more times)",
            sleepTime, tries - 1)
          Thread.sleep(sleepTime)
        }
        handle(f, tries - 1, sleepTime)

      case _ => None
    }

  override def equals(obj: Any): Boolean = obj match {
    case b: S3Bucket => b.bucketName == bucketName &&
      b.credentials.getAWSAccessKeyId == credentials.getAWSAccessKeyId &&
      b.credentials.getAWSSecretKey == credentials.getAWSSecretKey
    case _ => false
  }
}
