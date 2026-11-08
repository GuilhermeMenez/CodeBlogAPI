package blog.code.codeblog.model.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = UserDocument.INDEX, createIndex = false)
@Setting(settingPath = "elasticsearch/users-settings.json")
public class UserDocument {

    public static final String INDEX = "users";

    @Id
    private UUID id;

    @Field(type = FieldType.Search_As_You_Type, analyzer = "folding")
    private String name;
}
