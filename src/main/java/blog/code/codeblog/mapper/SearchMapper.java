package blog.code.codeblog.mapper;

import blog.code.codeblog.command.search.SearchPostsCommand;
import blog.code.codeblog.command.search.SearchUsersCommand;
import blog.code.codeblog.command.search.SuggestCommand;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SearchMapper {

    private static final int MAX_SIZE = 50;
    private static final int MAX_SUGGEST_LIMIT = 10;

    public SearchPostsCommand toSearchPostsCommand(String query, int page, int size) {
        return SearchPostsCommand.builder()
                .query(query.trim())
                .page(Math.max(page, 0))
                .size(Math.clamp(size, 1, MAX_SIZE))
                .build();
    }

    public SearchUsersCommand toSearchUsersCommand(String query, int page, int size) {
        return SearchUsersCommand.builder()
                .query(query.trim())
                .page(Math.max(page, 0))
                .size(Math.clamp(size, 1, MAX_SIZE))
                .build();
    }

    public SuggestCommand toSuggestCommand(String query, int limit) {
        return SuggestCommand.builder()
                .query(query.trim())
                .limit(Math.clamp(limit, 1, MAX_SUGGEST_LIMIT))
                .build();
    }
}
