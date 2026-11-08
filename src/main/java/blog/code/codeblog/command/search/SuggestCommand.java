package blog.code.codeblog.command.search;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SuggestCommand {
    String query;
    int limit;
}
