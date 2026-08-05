package com.im.platform.dfs.service;

import com.im.platform.common.core.constant.BizType;
import com.im.platform.common.core.exception.BizException;
import com.im.platform.common.core.exception.ErrorCode;
import com.im.platform.idgen.IdGenClient;
import com.im.platform.dfs.entity.FileEntity;
import com.im.platform.dfs.mapper.FileMapper;
import com.im.platform.dfs.storage.MinioStorageClient;
import org.springframework.stereotype.Service;

@Service
public class FileUploadService {

    private static final int UPLOAD_URL_EXPIRE_MINUTES = 15;
    private static final int DOWNLOAD_URL_EXPIRE_MINUTES = 60;

    private final FileMapper fileMapper;
    private final IdGenClient idGenClient;
    private final MinioStorageClient storageClient;

    public FileUploadService(FileMapper fileMapper, IdGenClient idGenClient, MinioStorageClient storageClient) {
        this.fileMapper = fileMapper;
        this.idGenClient = idGenClient;
        this.storageClient = storageClient;
    }

    public UploadTicketResult requestUpload(long ownerId, String fileName, long fileSize, String contentType) {
        long fileId = idGenClient.generateId(BizType.FILE_ID);
        FileEntity entity = new FileEntity();
        entity.setFileId(fileId);
        entity.setOwnerId(ownerId);
        entity.setFileName(fileName);
        entity.setObjectKey(ownerId + "/" + fileId + "/" + fileName);
        entity.setFileSize(fileSize);
        entity.setContentType(contentType);
        entity.setStatus(0);
        fileMapper.insert(entity);

        try {
            String uploadUrl = storageClient.presignedUploadUrl(entity.getObjectKey(), UPLOAD_URL_EXPIRE_MINUTES);
            long expireAt = System.currentTimeMillis() + UPLOAD_URL_EXPIRE_MINUTES * 60_000L;
            return new UploadTicketResult(entity, uploadUrl, expireAt);
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, e.getMessage());
        }
    }

    public record UploadTicketResult(FileEntity file, String uploadUrl, long expireAt) {
    }

    public FileEntity completeUpload(long fileId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "file not found: " + fileId);
        }
        entity.setStatus(1);
        fileMapper.updateById(entity);
        return entity;
    }

    public DownloadTicketResult presignedDownloadUrl(long fileId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "file not found: " + fileId);
        }
        try {
            String downloadUrl = storageClient.presignedDownloadUrl(entity.getObjectKey(), DOWNLOAD_URL_EXPIRE_MINUTES);
            long expireAt = System.currentTimeMillis() + DOWNLOAD_URL_EXPIRE_MINUTES * 60_000L;
            return new DownloadTicketResult(downloadUrl, expireAt);
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, e.getMessage());
        }
    }

    public record DownloadTicketResult(String downloadUrl, long expireAt) {
    }
}
