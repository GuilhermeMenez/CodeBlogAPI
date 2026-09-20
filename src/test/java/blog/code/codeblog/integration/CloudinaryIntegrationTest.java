package blog.code.codeblog.integration;

import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.service.TokenService;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import blog.code.codeblog.model.User;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CloudinaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private Cloudinary cloudinary;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private String authToken;

    @BeforeEach
    void setUp() throws IOException {
        testUser = new User();
        testUser.setName("Test User");
        testUser.setLogin("testuser@email.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser = userRepository.save(testUser);

        authToken = tokenService.generateToken(testUser);

        Uploader mockUploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(mockUploader);

        Map<String, Object> uploadResponse = new HashMap<>();
        uploadResponse.put("url", "https://cloudinary.com/test-image.jpg");
        uploadResponse.put("public_id", "test_folder/test-image");
        when(mockUploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResponse);

        Map<String, Object> deleteResponse = new HashMap<>();
        deleteResponse.put("result", "ok");
        when(mockUploader.destroy(anyString(), anyMap())).thenReturn(deleteResponse);
    }

    @Test
    @DisplayName("Should upload image with valid authentication")
    void uploadImageWithAuth() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        mockMvc.perform(multipart("/image/upload")
                        .file(file)
                        .param("flag", "PROFILE")
                        .param("userId", testUser.getId().toString())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Upload successful"))
                .andExpect(jsonPath("$.imageUrl").exists())
                .andExpect(jsonPath("$.publicId").exists());
    }

    @Test
    @DisplayName("Should reject upload without authentication")
    void uploadWithoutAuthShouldBeRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        mockMvc.perform(multipart("/image/upload")
                        .file(file)
                        .param("flag", "PROFILE")
                        .param("userId", testUser.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should delete image successfully")
    void deleteImageSuccess() throws Exception {
        String publicId = "test_folder/test-image";

        mockMvc.perform(delete("/image/delete")
                        .param("publicId", publicId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ok"));
    }


    @Test
    @DisplayName("Should reject delete without authentication")
    void deleteWithoutAuthShouldBeRejected() throws Exception {
        mockMvc.perform(delete("/image/delete")
                        .param("publicId", "some-public-id"))
                .andExpect(status().isUnauthorized());
    }
}