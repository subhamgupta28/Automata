package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "requestInfo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RequestInfo {
    @Id
    private String id;
    private String host;
    private String remoteAddr;
    private String requestURI;
    private String requestURL;
    private String method;
    private String queryString;
}
