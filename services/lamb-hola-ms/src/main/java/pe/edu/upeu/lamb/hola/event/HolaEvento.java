package pe.edu.upeu.lamb.hola.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolaEvento {

    private String tipoEvento;
    private Long id;
    private String mensaje;
    private String origen;
    private Long timestamp;
}
