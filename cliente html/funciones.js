/**
 * Inicia el proceso de streaming de una canción llamando a la implementación
 * gRPC-Web disponible en el objeto `window`.
 * 
 * Esta función actúa como un "wrapper" o intermediario, buscando varias
 * posibles implementaciones del cliente de streaming (cargadas desde `bundle.js`)
 * y ejecutando la primera que encuentre. Esto proporciona flexibilidad y
 * retrocompatibilidad con nombres de funciones antiguos.
 * 
 * Si no se encuentra ninguna implementación, registra un error en la consola y en la UI.
 *
 * @param {string} titulo El nombre del archivo de la canción a solicitar (ej. 'cancion1').
 * @param {string} formato El formato de la canción (ej. 'mp3' o 'wav').
 */
function pedirCancion(titulo, formato) {
    // Busca en `window` la función de streaming, probando varios nombres posibles.
    if (typeof window.iniciar_streaming_cancion === 'function') {
        return window.iniciar_streaming_cancion(titulo, formato);
    }
    if (typeof window.iniciar_streaming_cancion_impl === 'function') {
        return window.iniciar_streaming_cancion_impl(titulo, formato);
    }
    if (typeof window.iniciarStreamGRPCImpl === 'function') {
        return window.iniciarStreamGRPCImpl(titulo, formato);
    }
    if (typeof window.iniciarStreamGRPC === 'function') {
        return window.iniciarStreamGRPC(titulo, formato);
    }

    // Si no se encuentra ninguna función, se notifica el error.
    console.error('No se encontró ninguna implementación de iniciar_streaming_cancion.');
    const d = document.getElementById('log');
    if (d) {
        const p = document.createElement('div');
        p.className = 'error';
        p.textContent = 'No se encontró ninguna implementación de iniciar_streaming_cancion.';
        d.appendChild(p);
    }
}

// Exporta la función para compatibilidad con sistemas de módulos como CommonJS (usado por Webpack).
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { pedirCancion };
}

// -----------------------------------------------------------------------------
// ESTADO GLOBAL DEL CLIENTE
// Almacena las variables clave de la sesión actual del usuario.
// -----------------------------------------------------------------------------

/** 
 * La instancia del cliente STOMP una vez que la conexión WebSocket se establece.
 * Es `null` si no hay conexión activa.
 * @type {Stomp.Client | null} 
 */
let stompClient = null;

/** 
 * El identificador de la canción que se está reproduciendo actualmente (ej. 'cancion1').
 * Se usa para suscribirse y enviar mensajes al canal correcto del broker.
 */
let currentSongId = null;

/** 
 * El nickname del usuario actual, establecido desde el campo de entrada.
 * Se utiliza para identificar al usuario en los mensajes y para la conexión WebSocket.
 */
let currentNickname = null;

/** 
 * La suscripción activa al canal STOMP de la canción actual.
 * Se guarda para poder anular la suscripción al cambiar de canción.
 */
let currentSubscription = null;

// -----------------------------------------------------------------------------
// HELPERS DE UI
// Funciones dedicadas a manipular el DOM y mostrar información visual al usuario.
// -----------------------------------------------------------------------------

/**
 * Escribe un mensaje en el panel de "Línea de Tiempo" de la UI.
 * Cada mensaje se añade con una marca de tiempo.
 *
 * @param {string} message El texto del mensaje a mostrar.
 * @param {string} [level] Una clase CSS opcional ('success', 'error') para dar estilo al mensaje.
 */
function writeLog(message, level) {
    const d = document.getElementById('log');
    if (!d) return;

    const p = document.createElement('div');
    p.className = level || '';
    const ts = new Date().toLocaleTimeString();
    p.textContent = `[${ts}] ${message}`;
    d.appendChild(p);
    // Hace scroll automático para que el último mensaje sea siempre visible.
    d.scrollTop = d.scrollHeight;
}

/**
 * Muestra una burbuja de notificación simple en el centro del reproductor.
 * Utilizada específicamente para los eventos de PLAY y PAUSE.
 *
 * @param {string} text El texto a mostrar dentro de la burbuja (ej. 'Juanito ▶').
 */
function showReactionBubble(text) {
    const overlay = document.getElementById('reactions-overlay');
    if (!overlay) return;

    const bubble = document.createElement('div');
    bubble.className = 'reaction-bubble';
    bubble.textContent = text;

    // Coloca la burbuja en una posición horizontal aleatoria para un efecto visual menor.
    const randomLeft = 20 + Math.random() * 60;
    bubble.style.left = randomLeft + '%';

    overlay.appendChild(bubble);

    // Elimina la burbuja del DOM después de que su animación CSS haya terminado.
    setTimeout(() => {
        if (overlay.contains(bubble)) {
            overlay.removeChild(bubble);
        }
    }, 3000);
}

/**
 * Actualiza la lista de "Usuarios Activos" en la UI.
 * Añade o elimina un nickname de la lista basado en el tipo de evento recibido.
 *
 * @param {string} nickname El nickname del usuario que se une o se va.
 * @param {'PLAY' | 'PAUSE'} tipo El tipo de evento que determina si añadir o quitar al usuario.
 */
function updateUserListFromEvent(nickname, tipo) {
    const ul = document.getElementById('usuarios-lista');
    if (!ul || !nickname) return;

    if (tipo === 'PLAY') {
        // Solo añade el usuario si no está ya en la lista.
        const exists = Array.from(ul.children).some(li => li.dataset.user === nickname);
        if (!exists) {
            const li = document.createElement('li');
            li.dataset.user = nickname; // Usamos un data-attribute para identificarlo fácilmente.
            li.textContent = nickname;
            li.classList.add('user-event');
            ul.appendChild(li);
        }
    } else if (tipo === 'PAUSE') {
        // Busca y elimina el elemento 'li' correspondiente al usuario.
        Array.from(ul.children).forEach(li => {
            if (li.dataset.user === nickname) {
                ul.removeChild(li);
            }
        });
    }
}

/**
 * Muestra una notificación "toast" en la esquina superior derecha de la pantalla.
 * Se utiliza para mensajes privados del servidor, como errores de pago o advertencias.
 *
 * @param {object} notificationData Un objeto con los detalles de la notificación.
 * @param {string} notificationData.tipo Tipo de notificación (ej. 'ERROR_PAGO', 'LIMITE_ALCANZADO') para aplicar un estilo.
 * @param {string} notificationData.titulo El título de la notificación.
 * @param {string} notificationData.mensaje El cuerpo del mensaje.
 */
function showPrivateNotification({ tipo, titulo, mensaje }) {
    const container = document.querySelector('body'); // Adjuntamos al body para asegurar visibilidad.
    if (!container) return;

    const notification = document.createElement('div');
    notification.className = 'private-notification';

    // Añade una clase de estilo condicional basada en el tipo.
    if (tipo === 'ERROR_PAGO') {
        notification.classList.add('error');
    } else if (tipo === 'LIMITE_ALCANZADO') {
        notification.classList.add('warning');
    }

    const titleElem = document.createElement('h4');
    titleElem.textContent = titulo;
    notification.appendChild(titleElem);

    const messageElem = document.createElement('p');
    messageElem.textContent = mensaje;
    notification.appendChild(messageElem);

    container.appendChild(notification);

    // La notificación se elimina automáticamente del DOM después de 5 segundos.
    setTimeout(() => {
        if (container.contains(notification)) {
            container.removeChild(notification);
        }
    }, 5000);
}

// -----------------------------------------------------------------------------
// GESTIÓN DE LA CONEXIÓN WEBSOCKET / STOMP
// Funciones responsables de establecer, gestionar y cerrar la comunicación
// en tiempo real con el servidor de reacciones.
// -----------------------------------------------------------------------------

/**
 * Orquesta el proceso de conexión al servidor de reacciones.
 * 
 * Si ya existe una conexión activa, se desconecta de forma segura antes de
 * intentar establecer una nueva. Esto es crucial al cambiar de canción para
 * evitar suscripciones duplicadas.
 * 
 * Si no hay conexión, llama directamente a {@link procederConNuevaConexion}.
 */
function connectReacciones() {
    if (!currentNickname || !currentSongId) {
        writeLog('No se puede conectar a reacciones: falta nickname o id de canción.', 'error');
        return;
    }

    // Si ya estamos conectados, primero nos desconectamos limpiamente.
    if (stompClient && stompClient.connected) {
        if (currentSubscription) {
            currentSubscription.unsubscribe();
            currentSubscription = null;
        }
        // La desconexión es asíncrona; la nueva conexión se inicia en su callback.
        stompClient.disconnect(() => {
            writeLog('Conexión anterior de reacciones cerrada.');
            procederConNuevaConexion();
        });
    } else {
        // Si no hay conexión, procedemos a conectar directamente.
        procederConNuevaConexion();
    }
}

/**
 * Establece una nueva conexión WebSocket y se suscribe a los canales STOMP.
 * 
 * Esta función es el núcleo de la comunicación en tiempo real.
 * 1. Crea una URL de conexión que incluye el nickname del usuario como parámetro
 *    para su identificación en el backend durante el handshake.
 * 2. Utiliza SockJS para establecer una conexión compatible y la envuelve con STOMP.
 * 3. Una vez conectado, realiza dos suscripciones clave:
 *    - Al canal público de la canción (`/broker/canciones/...`), para recibir eventos
 *      de play, pause y reacciones de otros usuarios.
 *    - Al canal privado del usuario (`/user/queue/notificaciones`), para recibir
 *      mensajes directos del servidor (ej. errores de pago).
 */
function procederConNuevaConexion() {
    // Construye la URL incluyendo el nickname para que el HandshakeHandler del servidor nos identifique.
    const url = `http://localhost:5000/ws?nickname=${encodeURIComponent(currentNickname)}`;
    console.log('Conectando a SockJS con URL:', url);

    // Usa SockJS para una conexión robusta y compatible.
    const socket = new SockJS(url);
    stompClient = Stomp.over(socket);
    
    // Habilita logs de depuración de STOMP en la consola del navegador.
    stompClient.debug = (str) => {
        console.log('STOMP DEBUG:', str);
    };

    const headers = {
        login: currentNickname
    };

    // Intenta conectar al servidor con las cabeceras definidas.
    stompClient.connect(headers, () => {
        console.log('CONEXIÓN STOMP EXITOSA. Suscribiendo a canales...');
        writeLog('Conectado al servidor de reacciones.', 'success');

        // Suscripción al canal PÚBLICO de la canción.
        const publicDestino = `/broker/canciones/${currentSongId}`;
        currentSubscription = stompClient.subscribe(publicDestino, (message) => {
            if (!message.body) return;
            try {
                const data = JSON.parse(message.body);
                manejarMensajeReaccion(data);
            } catch (e) {
                console.error('Error parseando mensaje de reacciones:', e);
            }
        });

        // Suscripción al canal PRIVADO para notificaciones.
        stompClient.subscribe('/user/queue/notificaciones', (message) => {
            if (!message.body) return;
            try {
                const notificacion = JSON.parse(message.body);
                console.log('NOTIFICACIÓN PRIVADA RECIBIDA:', notificacion);
                showPrivateNotification(notificacion);
            } catch (e) {
                console.error('Error parseando notificación privada:', e);
            }
        });

    }, (error) => {
        console.error('Error detallado en conexión STOMP:', error);
        writeLog('Error al conectar al servidor de reacciones.', 'error');
    });
}

// ================================================================
//  Lógica para "Burbujas Laterales Ascendentes"
// ================================================================

// Mapeo de tipo de reacción a su emoji/icono correspondiente.
const reactionMap = {
    like: '👍',
    heart: '❤️',
    fire: '🔥',
};

/**
 * Crea y anima una burbuja de reacción flotante en la pantalla.
 *
 * @param {string} nickname El nombre del usuario que reacciona.
 * @param {string} reactionType El tipo de reacción (ej. 'like', 'heart').
 */
function showFloatingReaction(nickname, reactionType) {
    const overlay = document.getElementById('global-reactions-overlay');
    if (!overlay || !reactionMap[reactionType]) return;

    const bubble = document.createElement('div');
    bubble.className = 'reaction-float-bubble';

    // Creamos el contenido de la burbuja con el icono y el nickname
    bubble.innerHTML = `<span>${reactionMap[reactionType]}</span> ${nickname}`;

    // 1. Posición inicial: Decide si la burbuja sale por la izquierda o por la derecha.
    const side = Math.random() < 0.5 ? 'left' : 'right';
    const startX = side === 'left' 
        ? (10 + Math.random() * 20) // Entre 10% y 30% del borde izquierdo
        : (70 + Math.random() * 20); // Entre 70% y 90% del borde derecho
    bubble.style.setProperty('--start-x', `${startX}%`);

    // 2. Deriva horizontal final: Un desplazamiento lateral para una trayectoria curva.
    const xDrift = (Math.random() - 0.5) * 150; // Entre -75px y +75px
    bubble.style.setProperty('--x-drift', `${xDrift}px`);

    // 3. Duración de la animación: Para que no todas suban a la misma velocidad.
    const duration = 4 + Math.random() * 2; // Entre 4 y 6 segundos
    bubble.style.setProperty('--duration', `${duration}s`);

    // 4. Retardo: Para evitar que las ráfagas de reacciones se solapen perfectamente.
    const delay = Math.random() * 0.3; // Hasta 0.3 segundos de retardo
    bubble.style.setProperty('--delay', `${delay}s`);

    overlay.appendChild(bubble);

    // Limpia el elemento del DOM después de que la animación termine (duración + retardo).
    setTimeout(() => {
        if (overlay.contains(bubble)) {
            overlay.removeChild(bubble);
        }
    }, (duration + delay) * 1000);
}


/**
 * Procesa los mensajes recibidos desde el canal público de la canción.
 * 
 * Esta función es el callback principal para la suscripción STOMP. Clasifica el
 * mensaje entrante por su 'tipo' y actualiza la UI correspondientemente.
 * - Para 'PLAY'/'PAUSE', actualiza la lista de usuarios y muestra una notificación central.
 * - Para 'REACCION', invoca la animación de burbuja flotante, pero solo si el 
 *   reproductor de audio del usuario no está en pausa.
 *
 * @param {object} data El objeto del mensaje deserializado desde JSON.
 * @param {string} data.nickname El nickname del usuario que originó el evento.
 * @param {string} data.idCancion El ID de la canción a la que pertenece el evento.
 * @param {'PLAY' | 'PAUSE' | 'REACCION'} data.tipo El tipo de evento.
 * @param {string} [data.contenido] Contenido adicional (ej. el tipo de reacción).
 */
function manejarMensajeReaccion(data) {
    const { nickname, idCancion, tipo, contenido } = data;

    // Ignora el mensaje si no corresponde a la canción actual.
    if (!idCancion || idCancion !== currentSongId) {
        return;
    }

    const audioPlayer = document.getElementById('audio-player');

    // Gestiona eventos de estado (Play/Pause).
    if (tipo === 'PLAY') {
        updateUserListFromEvent(nickname, 'PLAY');
        writeLog(`${nickname} comenzó a reproducir la canción ${idCancion}.`, 'success');
        showReactionBubble(`${nickname} ▶`); // Usa la animación central simple.
    } else if (tipo === 'PAUSE') {
        updateUserListFromEvent(nickname, 'PAUSE');
        writeLog(`${nickname} pausó la canción ${idCancion}.`, 'error');
        showReactionBubble(`${nickname} ⏸`); // Usa la animación central simple.
    } 
    // Gestiona eventos de reacción.
    else if (tipo === 'REACCION') {
        // Filtro clave: Solo muestra la animación si el usuario está escuchando activamente.
        if (audioPlayer && !audioPlayer.paused) {
            writeLog(`${nickname} envió reacción: ${contenido}`, 'success');
            showFloatingReaction(nickname, contenido); // Llama a la animación de burbuja lateral.
        } else {
            console.log(`Reacción de ${nickname} ignorada porque el reproductor está pausado.`);
        }
    }
}

/**
 * Envía un mensaje STOMP al servidor a través de la conexión WebSocket activa.
 * 
 * Es una función de utilidad que verifica si la conexión está activa antes de enviar
 * el payload, que es serializado a JSON.
 *
 * @param {string} destino El destino STOMP en el servidor (ej. '/app/reaccionar').
 * @param {object} payload El objeto JavaScript que se enviará como cuerpo del mensaje.
 */
function enviarMensajeStomp(destino, payload) {
    if (!stompClient || !stompClient.connected) {
        writeLog('No hay conexión activa con el servidor de reacciones.', 'error');
        return;
    }
    stompClient.send(destino, {}, JSON.stringify(payload));
}

/**
 * Envía un mensaje 'PLAY' al servidor para notificar que el usuario ha
 * comenzado a reproducir la canción.
 * Utiliza los valores globales `currentNickname` y `currentSongId`.
 */
function enviarPlay() {
    if (!currentNickname || !currentSongId) return;
    enviarMensajeStomp('/app/reproducir', {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'PLAY',
        contenido: null
    });
}

/**
 * Envía un mensaje 'PAUSE' al servidor para notificar que el usuario ha
 * pausado la reproducción de la canción.
 * Utiliza los valores globales `currentNickname` y `currentSongId`.
 */
function enviarPause() {
    if (!currentNickname || !currentSongId) return;
    enviarMensajeStomp('/app/detener', {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'PAUSE',
        contenido: null
    });
}

/**
 * Envía un mensaje de 'REACCION' al servidor.
 * 
 * Antes de enviar, verifica que el reproductor de audio no esté en pausa.
 * Si lo está, muestra una notificación de error al usuario y cancela el envío.
 *
 * @param {string} tipoReaccion El tipo de reacción a enviar (ej. 'like', 'heart').
 */
function enviarReaccion(tipoReaccion) {
    if (!currentNickname || !currentSongId) {
        writeLog('No se puede enviar reacción: falta nickname o canción.', 'error');
        return;
    }

    // Validación de estado: no se permite reaccionar si la música está pausada.
    const audioPlayer = document.getElementById('audio-player');
    if (audioPlayer && audioPlayer.paused) {
        writeLog('No puedes reaccionar mientras la canción está pausada.', 'error');
        // Proporciona feedback inmediato al usuario.
        showPrivateNotification({
            tipo: 'ERROR_PAGO',
            titulo: 'Acción no permitida',
            mensaje: 'No puedes enviar reacciones mientras la canción está en pausa.'
        });
        return; // Detiene la ejecución.
    }
    
    // Si el reproductor está activo, envía el mensaje de reacción.
    enviarMensajeStomp('/app/reaccionar', {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'REACCION',
        contenido: tipoReaccion
    });
}
// -----------------------------------------------------------------------------
// LISTENERS DE EVENTOS DE AUDIO Y UI
// Conectan las acciones del usuario (clics, play/pause) con las funciones lógicas.
// -----------------------------------------------------------------------------

/**
 * Adjunta listeners a los eventos 'play' y 'pause' del reproductor de audio.
 * Cuando estos eventos se disparan, se llama a las funciones correspondientes
 * para notificar al servidor a través de WebSocket.
 */
function attachAudioListeners() {
    const audio = document.getElementById('audio-player');
    if (!audio) {
        writeLog('No se encontró el elemento audio#audio-player.', 'error');
        return;
    }

    // Cuando el usuario presiona 'play' en el reproductor.
    audio.addEventListener('play', function () {
        writeLog('Reproducción iniciada (play).', 'success');
        enviarPlay();
    });

    // Cuando el usuario presiona 'pause' en el reproductor.
    audio.addEventListener('pause', function () {
        writeLog('Reproducción pausada (pause).', 'error');
        enviarPause();
    });
}

/**
 * Adjunta listeners a los elementos de la interfaz de usuario, como botones.
 */
function attachUiListeners() {
    const btnPedir = document.getElementById('btn-pedir-cancion');
    if (btnPedir) {
        // Listener para el botón principal "Pedir Canción".
        btnPedir.addEventListener('click', () => {
            const nicknameInput = document.getElementById('nickname');
            const tituloInput = document.getElementById('titulo-cancion');
            const formatoSelect = document.getElementById('formato-cancion');

            const nickname = nicknameInput ? nicknameInput.value.trim() : '';
            const titulo = tituloInput ? tituloInput.value.trim() : '';
            const formato = formatoSelect ? formatoSelect.value : 'mp3';

            // Validación simple para asegurar que los campos no estén vacíos.
            if (!nickname || !titulo) {
                writeLog('Debes ingresar un nickname y un título de canción.', 'error');
                return;
            }

            // Actualiza el estado global con la información de la nueva sesión.
            currentNickname = nickname;
            currentSongId = titulo;

            writeLog(`Solicitando canción "${titulo}" en formato ${formato} para ${nickname}.`, 'success');

            // Inicia las dos operaciones principales: pedir el audio y conectar a reacciones.
            pedirCancion(titulo, formato);
            connectReacciones();
        });
    }

    // Listeners para todos los botones de reacción.
    const reactionButtons = document.querySelectorAll('.reaction-btn');
    reactionButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            // Obtiene el tipo de reacción desde el atributo 'data-reaccion' del botón.
            const tipoReaccion = btn.dataset.reaccion;
            enviarReaccion(tipoReaccion);
        });
    });
}

/**
 * IIFE (Immediately Invoked Function Expression) para asegurar que los listeners
 * se adjunten tan pronto como el DOM esté listo, ya sea que el script se cargue
 * de forma síncrona o asíncrona.
 */
(function () {
    if (document.readyState === 'loading') {
        // Si el DOM aún está cargando, espera al evento DOMContentLoaded.
        document.addEventListener('DOMContentLoaded', () => {
            attachAudioListeners();
            attachUiListeners();
        });
    } else {
        // Si el DOM ya está listo, ejecuta las funciones inmediatamente.
        attachAudioListeners();
        attachUiListeners();
    }
})();

// -----------------------------------------------------------------------------
// EXPORTACIÓN GLOBAL
// Expone funciones clave al objeto `window` para que puedan ser llamadas
// desde otros scripts, como el `bundle.js` generado por Webpack.
// -----------------------------------------------------------------------------
if (typeof window !== 'undefined') {
    window.pedirCancion = pedirCancion;
}