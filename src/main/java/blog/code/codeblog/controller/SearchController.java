package blog.code.codeblog.controller;

import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.search.SearchSuggestionsDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.facade.SearchFacade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    SearchFacade searchFacade;

    @GetMapping("/posts")
    @ResponseStatus(HttpStatus.OK)
    public PageResponseDTO<PostResponseDTO> searchPosts(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Search posts request received (q: {}, page: {}, size: {})", q, page, size);
        return searchFacade.searchPosts(q, page, size);
    }

    @GetMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    public PageResponseDTO<UserFollowDTO> searchUsers(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Search users request received (q: {}, page: {}, size: {})", q, page, size);
        return searchFacade.searchUsers(q, page, size);
    }

    @GetMapping("/suggest")
    @ResponseStatus(HttpStatus.OK)
    public SearchSuggestionsDTO suggest(
            @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Search suggest request received (q: {}, limit: {})", q, limit);
        return searchFacade.suggest(q, limit);
    }
}
