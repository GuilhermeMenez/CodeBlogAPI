package blog.code.codeblog.facade;

import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.search.SearchSuggestionsDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.mapper.SearchMapper;
import blog.code.codeblog.service.search.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SearchFacade {
    @Autowired
    SearchService searchService;

    public PageResponseDTO<PostResponseDTO> searchPosts(String q, int page, int size) {
        return searchService.searchPosts(SearchMapper.toSearchPostsCommand(q, page, size));
    }

    public PageResponseDTO<UserFollowDTO> searchUsers(String q, int page, int size) {
        return searchService.searchUsers(SearchMapper.toSearchUsersCommand(q, page, size));
    }

    public SearchSuggestionsDTO suggest(String q, int limit) {
        return searchService.suggest(SearchMapper.toSuggestCommand(q, limit));
    }
}
