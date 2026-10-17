package blog.code.codeblog.dto.follow;


import java.util.UUID;

public record FollowUnfollowRequestDTO(UUID followedId)
{
}
