package com.im.platform.dfs.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * MinIO 客户端封装。本服务只下发预签名 URL,实际文件字节流由客户端直接与 MinIO 交互,
 * 不经过 dfs 服务转发,避免文件流量把业务服务的连接池打满。
 */
@Component
public class MinioStorageClient {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageClient(@Value("${dfs.minio.endpoint}") String endpoint,
                               @Value("${dfs.minio.access-key}") String accessKey,
                               @Value("${dfs.minio.secret-key}") String secretKey,
                               @Value("${dfs.minio.bucket}") String bucket) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    public String presignedUploadUrl(String objectName, int expireMinutes) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectName)
                .expiry(expireMinutes, TimeUnit.MINUTES)
                .build());
    }

    public String presignedDownloadUrl(String objectName, int expireMinutes) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(expireMinutes, TimeUnit.MINUTES)
                .build());
    }
}
