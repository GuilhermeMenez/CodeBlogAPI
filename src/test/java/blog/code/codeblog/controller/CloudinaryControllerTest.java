package blog.code.codeblog.controller;

import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.service.CloudinaryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryControllerTest {

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private CloudinaryController cloudinaryController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private MultipartFile createMockFile() {
        return new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
    }

    @Test
    @DisplayName("Should upload profile image successfully")
    void uploadProfileImageSuccess() throws IOException {
        // Arrange
        UUID userId = UUID.randomUUID();
        String imageUrl = "https://cloudinary.com/profile_pics/test-image.jpg";
        String publicId = "profile_pics/test-image";
        MultipartFile file = createMockFile();
        ImageUploadResponseDTO expectedResponse = new ImageUploadResponseDTO(
                "Profile pic updated successfully", imageUrl, publicId);

        when(cloudinaryService.uploadFile(eq(file), eq(FlowImageFlag.PROFILE), eq(userId.toString()), isNull()))
                .thenReturn(expectedResponse);

        // Act
        ImageUploadResponseDTO result = cloudinaryController.uploadImage(file, FlowImageFlag.PROFILE, userId.toString(), null);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse.message(), result.message());
        assertEquals(expectedResponse.imageUrl(), result.imageUrl());
        assertEquals(expectedResponse.publicId(), result.publicId());
        verify(cloudinaryService).uploadFile(eq(file), eq(FlowImageFlag.PROFILE), eq(userId.toString()), isNull());
    }

    @Test
    @DisplayName("Should upload post image successfully")
    void uploadPostImageSuccess() throws IOException {
        // Arrange
        UUID postId = UUID.randomUUID();
        String imageUrl = "https://cloudinary.com/post_pics/test-image.jpg";
        String publicId = "post_pics/test-image";
        MultipartFile file = createMockFile();
        ImageUploadResponseDTO expectedResponse = new ImageUploadResponseDTO(
                "Image uploaded", imageUrl, publicId);

        when(cloudinaryService.uploadFile(eq(file), eq(FlowImageFlag.POST), isNull(), eq(postId.toString())))
                .thenReturn(expectedResponse);

        // Act
        ImageUploadResponseDTO result = cloudinaryController.uploadImage(file, FlowImageFlag.POST, null, postId.toString());

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse.message(), result.message());
        assertEquals(expectedResponse.imageUrl(), result.imageUrl());
        assertEquals(expectedResponse.publicId(), result.publicId());
        verify(cloudinaryService).uploadFile(eq(file), eq(FlowImageFlag.POST), isNull(), eq(postId.toString()));
    }

    @Test
    @DisplayName("Should throw IOException when upload fails")
    void uploadImageShouldThrowWhenServiceFails() throws IOException {
        // Arrange
        MultipartFile file = createMockFile();

        when(cloudinaryService.uploadFile(any(MultipartFile.class), any(), anyString(), isNull()))
                .thenThrow(new IOException("Upload failed"));

        // Act & Assert
        assertThrows(IOException.class, () ->
            cloudinaryController.uploadImage(file, FlowImageFlag.PROFILE, UUID.randomUUID().toString(), null)
        );
    }

    @Test
    @DisplayName("Should delete image successfully")
    void deleteImageSuccess() throws IOException {
        // Arrange
        String publicId = "post_pics/test-image";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("result", "ok");

        when(cloudinaryService.deleteFile(publicId)).thenReturn(expectedResponse);

        // Act
        Map<String, Object> result = cloudinaryController.deleteImage(publicId);

        // Assert
        assertNotNull(result);
        assertEquals("ok", result.get("result"));
        verify(cloudinaryService).deleteFile(publicId);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when deleting non-existent image")
    void deleteImageNotFound() throws IOException {
        // Arrange
        String publicId = "unknown/test-image";

        when(cloudinaryService.deleteFile(publicId))
                .thenThrow(new EntityNotFoundException("Image with publicId " + publicId + " not found"));

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
            cloudinaryController.deleteImage(publicId)
        );
        assertEquals("Image with publicId " + publicId + " not found", exception.getMessage());
        verify(cloudinaryService).deleteFile(publicId);
    }

    @Test
    @DisplayName("Should throw IOException when delete fails at Cloudinary")
    void deleteImageCloudinaryError() throws IOException {
        // Arrange
        String publicId = "post_pics/test-image";

        when(cloudinaryService.deleteFile(publicId))
                .thenThrow(new IOException("Cloudinary delete failed"));

        // Act & Assert
        assertThrows(IOException.class, () ->
            cloudinaryController.deleteImage(publicId)
        );
        verify(cloudinaryService).deleteFile(publicId);
    }
}
