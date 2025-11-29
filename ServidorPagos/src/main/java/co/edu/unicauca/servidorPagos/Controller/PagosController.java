package co.edu.unicauca.servidorPagos.Controller;

import co.edu.unicauca.servidorPagos.Models.PagoRequest;
import co.edu.unicauca.servidorPagos.Models.PagoResponse;
import co.edu.unicauca.servidorPagos.Models.TokenResponse;
import co.edu.unicauca.servidorPagos.Service.PagosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST del servidor de pagos.
 *
 * <p>Expone endpoints para:</p>
 * <ul>
 *   <li>Generar un nuevo token de pago.</li>
 *   <li>Registrar un pago asociado a una reacción.</li>
 * </ul>
 *
 * <p>El servidor de reacciones consumirá estos endpoints para cumplir
 * con los requerimientos de token único, límite de $50 y simulación
 * de fallos en los pagos.</p>
 */
@RestController
@RequestMapping("/api/pagos")
@CrossOrigin(origins = "*")
public class PagosController {

    /**
     * Servicio de negocio que contiene la lógica de generación
     * de tokens y registro de pagos.
     */
    @Autowired
    private PagosService pagosService;

    /**
     * Endpoint para solicitar la generación de un nuevo token.
     *
     * <p>Método: {@code POST}</p>
     * <p>Ruta: {@code /api/pagos/token}</p>
     *
     * <p>No requiere cuerpo en la petición. Devuelve un objeto
     * {@link TokenResponse} con el valor del token generado.</p>
     *
     * @return respuesta HTTP con el token generado
     */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> generarToken() {
        TokenResponse response = pagosService.generarToken();
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para registrar un nuevo pago asociado a una reacción.
     *
     * <p>Método: {@code POST}</p>
     * <p>Ruta: {@code /api/pagos}</p>
     *
     * <p>El cuerpo de la petición debe contener un {@link PagoRequest}
     * en formato JSON. El servidor aplicará las reglas de negocio:
     * token único, límite de $50 por usuario y posible simulación
     * de errores para probar reintentos.</p>
     *
     * <p>Devuelve un {@link PagoResponse} indicando el resultado
     * del procesamiento del pago.</p>
     *
     * @param request datos del pago a registrar
     * @return respuesta HTTP con el resultado del pago
     */
    @PostMapping
    public ResponseEntity<PagoResponse> registrarPago(@RequestBody PagoRequest request) {
        PagoResponse response = pagosService.registrarPago(request);
        return ResponseEntity.ok(response);
    }
}
