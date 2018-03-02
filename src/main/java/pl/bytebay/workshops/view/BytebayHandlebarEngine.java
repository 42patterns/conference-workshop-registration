package pl.bytebay.workshops.view;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.List;
import java.util.Map;

public class BytebayHandlebarEngine extends HandlebarsTemplateEngine {

    public BytebayHandlebarEngine() {
        super();
        handlebars.registerHelper("breaklines", (Helper<String>) (s, options) -> {
            String text = s.replaceAll("(\\r\\n|\\n|\\r)", "<br>");
            return new Handlebars.SafeString(text);
        });

        handlebars.registerHelper("ifIn", (Helper<String>) (s, options) -> {
            List<String> collection = options.param(0);
            return collection.contains(s)?"checked":"";
        });

        handlebars.registerHelper("value", (Helper<String>) (s, options) -> {
            Map<String, Integer> popularity = options.param(0);
            return popularity.getOrDefault(s, 0);
        });

        handlebars.registerHelper("ifOver", (Helper<Integer>) (capacity, options) -> {
            Map<String, Integer> popularity = options.param(0);
            String title = options.param(1);
            return (popularity.getOrDefault(title, 0) >= capacity)?"disabled":"";
        });
    }
}
