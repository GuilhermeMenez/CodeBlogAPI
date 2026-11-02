package blog.code.codeblog.command.search;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SearchPostsCommand {
    String query;
    int page;
    int size;
}
