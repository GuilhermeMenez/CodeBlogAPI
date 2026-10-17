package blog.code.codeblog.messaging.listener;

import blog.code.codeblog.messaging.event.ImageUploadCompletedEvent;
import blog.code.codeblog.service.PostService;
import blog.code.codeblog.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class ImageUploadCompletedListener {

    @Autowired
    private UserService userService;

    @Autowired
    private PostService postService;

    @EventListener(condition = "#event.flag() == T(blog.code.codeblog.enums.FlowImageFlag).PROFILE")
    public void onProfileImageUploaded(ImageUploadCompletedEvent event) {
        log.info("[onProfileImageUploaded] Saving profile image for userId: {}", event.userId());
        userService.onProfileImageUploaded(event);
    }

    @EventListener(condition = "#event.flag() == T(blog.code.codeblog.enums.FlowImageFlag).POST")
    public void onPostImageUploaded(ImageUploadCompletedEvent event) {
        log.info("[onPostImageUploaded] Saving post image for postId: {}", event.postId());
        postService.onPostImageUploaded(event);
    }
}
