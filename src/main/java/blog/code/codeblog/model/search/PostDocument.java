package blog.code.codeblog.model.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = PostDocument.INDEX, createIndex = false)
@Setting(settingPath = "elasticsearch/posts-settings.json")
public class PostDocument {

    public static final String INDEX = "posts";

    @Id
    private UUID id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "pt_br"),
            otherFields = {
                    @InnerField(suffix = "en", type = FieldType.Text, analyzer = "en"),
                    @InnerField(suffix = "suggest", type = FieldType.Search_As_You_Type, analyzer = "folding")})
    private String title;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "pt_br"),
            otherFields = @InnerField(suffix = "en", type = FieldType.Text, analyzer = "en"))
    private String content;

    @Field(type = FieldType.Keyword)
    private UUID authorId;

    @Field(type = FieldType.Text, analyzer = "folding")
    private String authorName;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate createdAt;
}
