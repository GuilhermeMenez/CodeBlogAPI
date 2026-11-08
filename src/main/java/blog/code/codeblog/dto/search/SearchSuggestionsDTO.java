package blog.code.codeblog.dto.search;

import lombok.Builder;

import java.io.Serializable;
import java.util.List;

@Builder
public record SearchSuggestionsDTO(List<PostSuggestionDTO> posts, List<UserSuggestionDTO> users) implements Serializable {}
