package blog.code.codeblog.service;

import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.service.interfaces.CloudinaryServiceInterface;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService implements CloudinaryServiceInterface {

    private static final String URL_KEY = "url";
    private static final String PUBLIC_ID_KEY = "public_id";
    private static final String POST_FOLDER = "post_pics";
    private static final String PROFILE_FOLDER = "profile_pics";

    private final Cloudinary cloudinary;

    public ImageUploadResponseDTO uploadFile(MultipartFile file, FlowImageFlag flag, String userId, String postId) throws IOException {
        return uploadFile(file.getBytes(), flag, userId, postId);
    }

    public ImageUploadResponseDTO uploadFile(byte[] content, FlowImageFlag flag, String userId, String postId) throws IOException {
        logUploadStart(flag, userId, postId);
        return uploadToCloudinary(content, flag);
    }


    private void logUploadStart(FlowImageFlag flag, String userId, String postId) {
        switch (flag) {
            case POST -> log.info("Starting upload for post: {}", postId);
            case PROFILE -> log.info("Starting upload for user: {}", userId);
        }
    }

    @SuppressWarnings("unchecked")
    private ImageUploadResponseDTO uploadToCloudinary(byte[] content, FlowImageFlag flag) throws IOException {
        String folder = flag == FlowImageFlag.POST ? POST_FOLDER : PROFILE_FOLDER;
        try {
            Map<String, Object> response = cloudinary.uploader().upload(content, ObjectUtils.asMap("folder", folder));
            log.info("Upload completed. PublicId: {}", response.get(PUBLIC_ID_KEY));

            return processUploadResult(response);
        } catch (IOException e) {
            log.error("Upload to Cloudinary failed: {}", e.getMessage());
            throw e;
        }
    }

    private ImageUploadResponseDTO processUploadResult(Map<String, Object> uploadResult) {
        String imageUrl = extractString(uploadResult, URL_KEY);
        String publicId = extractString(uploadResult, PUBLIC_ID_KEY);

        return ImageUploadResponseDTO.builder()
                .message("Upload successful")
                .imageUrl(imageUrl)
                .publicId(publicId)
                .build();
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required field in Cloudinary response: " + key);
        }
        return value.toString();
    }


    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteFile(String publicId) throws IOException {
        log.info("Deleting file with publicId: {}", publicId);
        try {
            return cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Could not delete image with: {}", publicId);
            throw new IOException("Failed to delete image with publicId: " + publicId, e);

        }
    }

}