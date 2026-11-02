package blog.code.codeblog.mapper;

import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.search.PostDocument;
import blog.code.codeblog.model.search.UserDocument;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SearchDocumentMapper {

    public PostDocument toPostDocument(Post post) {
        return PostDocument.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .authorId(post.getUser().getId())
                .authorName(post.getUser().getName())
                .createdAt(post.getDate())
                .build();
    }

    public UserDocument toUserDocument(User user) {
        return UserDocument.builder().id(user.getId()).name(user.getName()).build();
    }

}
