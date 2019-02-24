package patterns42.workshops.agenda.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class Speaker {
    String name;
    String bio;
    String photo;
}