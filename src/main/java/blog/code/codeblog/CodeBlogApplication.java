package blog.code.codeblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@SpringBootApplication
public class CodeBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeBlogApplication.class, args);

    }

}
