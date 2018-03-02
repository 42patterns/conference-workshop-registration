package pl.bytebay.workshops;

import com.fasterxml.jackson.databind.ObjectMapper;
import spark.ResponseTransformer;

public class JsonTransformer implements ResponseTransformer {

    @Override
    public String render(Object model) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(model);
    }
}
