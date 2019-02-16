package patterns42.workshops.agenda.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Speaker {
    Integer id;
    String name;
    String surname;
    String bio;
    String thumbnailUrl;
}