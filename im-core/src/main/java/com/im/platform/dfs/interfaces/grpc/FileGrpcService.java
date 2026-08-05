package com.im.platform.dfs.interfaces.grpc;

import com.im.platform.dfs.entity.FileEntity;
import com.im.platform.dfs.grpc.CompleteUploadRequest;
import com.im.platform.dfs.grpc.DownloadUrlResponse;
import com.im.platform.dfs.grpc.FileInfo;
import com.im.platform.dfs.grpc.FileServiceGrpc;
import com.im.platform.dfs.grpc.GetDownloadUrlRequest;
import com.im.platform.dfs.grpc.UploadRequest;
import com.im.platform.dfs.grpc.UploadTicket;
import com.im.platform.dfs.service.FileUploadService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class FileGrpcService extends FileServiceGrpc.FileServiceImplBase {

    private final FileUploadService fileUploadService;

    public FileGrpcService(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    public void requestUpload(UploadRequest request, StreamObserver<UploadTicket> responseObserver) {
        FileUploadService.UploadTicketResult result = fileUploadService.requestUpload(
                request.getOwnerId(), request.getFileName(), request.getFileSize(), request.getContentType());

        responseObserver.onNext(UploadTicket.newBuilder()
                .setFileId(result.file().getFileId())
                .setUploadUrl(result.uploadUrl())
                .setExpireAt(result.expireAt())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void completeUpload(CompleteUploadRequest request, StreamObserver<FileInfo> responseObserver) {
        FileEntity entity = fileUploadService.completeUpload(request.getFileId());
        responseObserver.onNext(toProto(entity));
        responseObserver.onCompleted();
    }

    @Override
    public void getDownloadUrl(GetDownloadUrlRequest request, StreamObserver<DownloadUrlResponse> responseObserver) {
        FileUploadService.DownloadTicketResult result = fileUploadService.presignedDownloadUrl(request.getFileId());
        responseObserver.onNext(DownloadUrlResponse.newBuilder()
                .setDownloadUrl(result.downloadUrl())
                .setExpireAt(result.expireAt())
                .build());
        responseObserver.onCompleted();
    }

    private FileInfo toProto(FileEntity entity) {
        return FileInfo.newBuilder()
                .setFileId(entity.getFileId())
                .setFileName(entity.getFileName() == null ? "" : entity.getFileName())
                .setFileSize(entity.getFileSize())
                .setContentType(entity.getContentType() == null ? "" : entity.getContentType())
                .build();
    }
}
