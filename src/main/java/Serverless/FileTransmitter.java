package Serverless;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;

public class FileTransmitter {
    private static final String FILE_NAME = "/tmp/downloaded.zip";

//    public static void downloadFile(String fileURL) throws IOException {
//
//        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
//            HttpGet request = new HttpGet(fileURL);
//
//            try (CloseableHttpResponse response = httpClient.execute(request);
//                 BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent());
//                 FileOutputStream fis = new FileOutputStream(FILE_NAME)) {
//
//                byte[] buffer = new byte[1024];
//                int count;
//                while ((count = bis.read(buffer, 0, 1024)) != -1) {
//                    fis.write(buffer, 0, count);
//                }
//            }
//        }
//    }
public static void downloadFile(Context ctx, String fileURL, String receiverEmail, String mailgunAPIKey, String dynamoDBTableName) throws IOException, UnirestException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        HttpGet request = new HttpGet(fileURL);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                // If status code is not OK, handle it here (e.g., throw an exception or return a status message)
                EmailSenderMailGun.sendSimpleMessage(ctx, mailgunAPIKey, receiverEmail, "Assignment Submission Failed",
                        "Your submission cannot be downloaded from the URL provided.", dynamoDBTableName);
                throw new IOException("Failed to download file: HTTP error code: " + statusCode);
            }

            // Check if the content length is 0, which means there's nothing to download
            long contentLength = response.getEntity().getContentLength();
            if (contentLength == 0) {
                EmailSenderMailGun.sendSimpleMessage(ctx, mailgunAPIKey, receiverEmail, "Assignment Submission Failed",
                        "Your submission file downloaded from the URL provided is emplty.", dynamoDBTableName);
                throw new IOException("Failed to download file: File is empty");
            }

            // Proceed with download if status is OK and content is available
            try (BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent());
                 FileOutputStream fis = new FileOutputStream(FILE_NAME)) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = bis.read(buffer, 0, 1024)) != -1) {
                    fis.write(buffer, 0, count);
                }
            }
        }
    }
}

    public static String uploadToGCS(Context ctx, String bucketName, String objectName, String receiverEmail, String mailgunAPIKey, String dynamoDBTableName) throws IOException, UnirestException {

        String serviceAccountKey = System.getenv("GCP_SERVICE_ACCOUNT_KEY");
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(serviceAccountKey.getBytes(StandardCharsets.UTF_8))
        ).createScoped("https://www.googleapis.com/auth/cloud-platform");


        // Use the credentials to authenticate and get the storage service
        Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();

        ctx.getLogger().log("storage created");
        BlobId blobId = BlobId.of(bucketName, objectName);
        ctx.getLogger().log("blobId created");
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        ctx.getLogger().log("blobInfo created");

        try (FileInputStream fis = new FileInputStream(FILE_NAME)) {
            storage.create(blobInfo, fis);
            ctx.getLogger().log("storage created with file name: " + FILE_NAME);

            EmailSenderMailGun.sendSimpleMessage(ctx, mailgunAPIKey, receiverEmail, "Assignment Submission Succeeded",
                    "Your submission is submitted successfully! Path in GCP bucket: " + bucketName + objectName, dynamoDBTableName);
            return "success upload";
        } catch (Exception e) {
            ctx.getLogger().log("Error uploading file: " + e.getMessage());

            EmailSenderMailGun.sendSimpleMessage(ctx,mailgunAPIKey, receiverEmail, "Assignment Submission Failed",
                    "Your submission is downloaded successfully, but failed uploading." + e.getMessage(), dynamoDBTableName);

            return "upload fail" + e.getMessage();
        }
    }
}
