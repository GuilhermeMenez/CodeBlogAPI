package blog.code.codeblog.dto.search;

import lombok.Builder;

import java.io.Serializable;
import java.util.UUID;

@Builder
public record UserSuggestionDTO(UUID id, String name) implements Serializable {}
