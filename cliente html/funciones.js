// funcion.js - wrapper simple para llamar a la implementación del streaming

/**
 * Solicita al backend de streaming que inicie el envío de la canción
 * usando la mejor implementación disponible expuesta en el objeto window.
 *
 * Orden de prioridad:
 *  - window.iniciar_streaming_cancion
 *  - window.iniciar_streaming_cancion_impl
 *  - window.iniciarStreamGRPCImpl
 *  - window.iniciarStreamGRPC
 *
 * Si ninguna implementación está disponible, se escribe un error en la consola
 * y en el panel de log de la página.
 *
 * @param {string} titulo  Título o identificador de la canción a reproducir.
 * @param {string} formato Formato de la canción (por ejemplo, "mp3" o "wav").
 * @returns {void}
 */
function pedirCancion(titulo, formato) {
    // Prioridad de implementaciones conocidas
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

    console.error('No se encontró ninguna implementación de iniciar_streaming_cancion.');
    const d = document.getElementById('log');
    if (d) {
        const p = document.createElement('div');
        p.className = 'error';
        p.textContent = 'No se encontró ninguna implementación de iniciar_streaming_cancion.';
        d.appendChild(p);
    }
}

// Export para compatibilidad con cargas como módulo (si fuese necesario)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { pedirCancion };
}

// --------------------------------------------------------
// --- Capa de WebSocket/Reacciones (STOMP + animaciones)
// --------------------------------------------------------

/**
 * Cliente STOMP sobre SockJS para comunicarse con el servidor de reacciones.
 * @type {Stomp.Client|null}
 */
let stompClient = null;

/**
 * Identificador de la canción actualmente solicitada/reproduciendo.
 * Se usa como idCancion para agrupar reacciones.
 * @type {string|null}
 */
let currentSongId = null;

/**
 * Nickname actual del usuario.
 * @type {string|null}
 */
let currentNickname = null;

/**
 * Suscripción actual al canal de reacciones de la canción.
 * @type {Stomp.Subscription|null}
 */
let currentSubscription = null;

/**
 * Escribe un mensaje en el panel de log (#log) con un estilo opcional.
 *
 * @param {string} message Mensaje a mostrar en el log.
 * @param {string} [level] Nivel o clase CSS opcional (por ejemplo, "success" o "error").
 * @returns {void}
 */
function writeLog(message, level) {
    const d = document.getElementById('log');
    if (!d) return;
    const p = document.createElement('div');
    p.className = level || '';
    const ts = new Date().toLocaleTimeString();
    p.textContent = `[${ts}] ${message}`;
    d.appendChild(p);
    d.scrollTop = d.scrollHeight;
}

/**
 * Crea una burbuja de animación en el overlay de reacciones, mostrando
 * un texto corto (por ejemplo, "Juan envió 👍").
 *
 * @param {string} text Texto a mostrar en la burbuja de reacción.
 * @returns {void}
 */
function showReactionBubble(text) {
    const overlay = document.getElementById('reactions-overlay');
    if (!overlay) return;

    const bubble = document.createElement('div');
    bubble.className = 'reaction-bubble';
    bubble.textContent = text;

    // Posición horizontal aleatoria dentro del overlay
    const randomLeft = 20 + Math.random() * 60; // entre 20% y 80%
    bubble.style.left = randomLeft + '%';

    overlay.appendChild(bubble);

    // Eliminar la burbuja cuando termine la animación
    setTimeout(() => {
        overlay.removeChild(bubble);
    }, 2000);
}

/**
 * Actualiza la lista de usuarios activos (#usuarios-lista) de forma simple:
 * cuando llega un evento PLAY agrega (si no está) y cuando llega un
 * evento PAUSE lo quita.
 *
 * @param {string} nickname Nickname del usuario.
 * @param {string} tipo     Tipo de evento ("PLAY" o "PAUSE" o "REACCION").
 * @returns {void}
 */
function updateUserListFromEvent(nickname, tipo) {
    const ul = document.getElementById('usuarios-lista');
    if (!ul || !nickname) return;

    if (tipo === 'PLAY') {
        // Agregar si no existe
        const exists = Array.from(ul.children).some(li => li.dataset.user === nickname);
        if (!exists) {
            const li = document.createElement('li');
            li.dataset.user = nickname;
            li.textContent = nickname;
            li.classList.add('user-event');
            ul.appendChild(li);
        }
    } else if (tipo === 'PAUSE') {
        // Eliminar si existe
        Array.from(ul.children).forEach(li => {
            if (li.dataset.user === nickname) {
                ul.removeChild(li);
            }
        });
    }
}

/**
 * Conecta con el servidor de reacciones usando SockJS/STOMP y se suscribe
 * al canal de la canción actual: /broker/canciones/{currentSongId}.
 *
 * Debe llamarse después de establecer currentNickname y currentSongId.
 *
 * @returns {void}
 */
function connectReacciones() {
    if (!currentNickname || !currentSongId) {
        writeLog('No se puede conectar a reacciones: falta nickname o id de canción.', 'error');
        return;
    }

    // Si ya hay un cliente conectado, cerrarlo antes de reconectar
    if (stompClient && stompClient.connected) {
        if (currentSubscription) {
            currentSubscription.unsubscribe();
            currentSubscription = null;
        }
        stompClient.disconnect(() => {
            writeLog('Desconectado de servidor de reacciones (reconexión).', 'error');
        });
    }

    const socket = new SockJS('http://localhost:5000/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silencia logs de debug si molestan

    stompClient.connect({}, () => {
        writeLog('Conectado al servidor de reacciones.', 'success');

        const destino = `/broker/canciones/${currentSongId}`;
        currentSubscription = stompClient.subscribe(destino, (message) => {
            if (!message.body) return;
            try {
                const data = JSON.parse(message.body);
                manejarMensajeReaccion(data);
            } catch (e) {
                console.error('Error parseando mensaje de reacciones:', e);
            }
        });
    }, (error) => {
        console.error('Error en conexión STOMP:', error);
        writeLog('Error al conectar al servidor de reacciones.', 'error');
    });
}

/**
 * Maneja los mensajes entrantes desde el servidor de reacciones
 * para la canción actual. data debe seguir la estructura de MensajeCancion.
 *
 * @param {{nickname:string, idCancion:string, tipo:string, contenido?:string}} data
 * @returns {void}
 */
function manejarMensajeReaccion(data) {
    const { nickname, idCancion, tipo, contenido } = data;

    if (!idCancion || idCancion !== currentSongId) {
        // Mensaje de otra canción; se ignora en este cliente.
        return;
    }

    if (tipo === 'PLAY') {
        updateUserListFromEvent(nickname, 'PLAY');
        writeLog(`${nickname} comenzó a reproducir la canción ${idCancion}.`, 'success');
        showReactionBubble(`${nickname} ▶`);
    } else if (tipo === 'PAUSE') {
        updateUserListFromEvent(nickname, 'PAUSE');
        writeLog(`${nickname} pausó la canción ${idCancion}.`, 'error');
        showReactionBubble(`${nickname} ⏸`);
    } else if (tipo === 'REACCION') {
        writeLog(`${nickname} envió reacción: ${contenido}`, 'success');
        showReactionBubble(`${nickname}: ${contenido || 'reacción'}`);
    }
}

/**
 * Envía un mensaje genérico al servidor de reacciones usando STOMP,
 * si la conexión está activa.
 *
 * @param {string} destino   Destino STOMP (por ejemplo, "/app/reproducir").
 * @param {Object} payload   Cuerpo del mensaje a enviar.
 * @returns {void}
 */
function enviarMensajeStomp(destino, payload) {
    if (!stompClient || !stompClient.connected) {
        writeLog('No hay conexión activa con el servidor de reacciones.', 'error');
        return;
    }
    stompClient.send(destino, {}, JSON.stringify(payload));
}

/**
 * Envía al servidor de reacciones un evento de inicio/reanudación de reproducción
 * para la canción actual.
 *
 * @returns {void}
 */
function enviarPlay() {
    if (!currentNickname || !currentSongId) return;

    const mensaje = {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'PLAY',
        contenido: null
    };
    enviarMensajeStomp('/app/reproducir', mensaje);
}

/**
 * Envía al servidor de reacciones un evento de pausa de reproducción
 * para la canción actual.
 *
 * @returns {void}
 */
function enviarPause() {
    if (!currentNickname || !currentSongId) return;

    const mensaje = {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'PAUSE',
        contenido: null
    };
    enviarMensajeStomp('/app/detener', mensaje);
}

/**
 * Envía al servidor de reacciones un evento de REACCION asociado
 * a la canción y usuario actuales.
 *
 * @param {string} tipoReaccion Tipo de reacción (por ejemplo "like", "heart", "fire").
 * @returns {void}
 */
function enviarReaccion(tipoReaccion) {
    if (!currentNickname || !currentSongId) {
        writeLog('No se puede enviar reacción: falta nickname o canción.', 'error');
        return;
    }

    const mensaje = {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'REACCION',
        contenido: tipoReaccion
    };
    enviarMensajeStomp('/app/reaccionar', mensaje);
}

// --------------------------------------------------------
// --- Helpers y listeners para logging de audio (play / pause)
//     integrados con la capa de reacciones
// --------------------------------------------------------
(function() {
    /**
     * Adjunta listeners de eventos al elemento de audio principal
     * (audio#audio-player) para:
     *  - Registrar en el log cuando se hace play/pause.
     *  - Notificar al servidor de reacciones (enviarPlay/enviarPause).
     *
     * Si el elemento de audio no se encuentra, se registra un error en el log.
     *
     * @returns {void}
     */
    function attachAudioListeners() {
        const audio = document.getElementById('audio-player');
        if (!audio) {
            writeLog('No se encontró el elemento audio#audio-player.', 'error');
            return;
        }

        audio.addEventListener('play', function() {
            writeLog('Reproducción iniciada (play).', 'success');
            enviarPlay();
        });

        audio.addEventListener('pause', function() {
            writeLog('Reproducción pausada (pause).', 'error');
            enviarPause();
        });
    }

    /**
     * Adjunta listeners a los elementos de la interfaz:
     *  - Botón "Pedir canción" para iniciar streaming y conectar a reacciones.
     *  - Botones de reacción para enviar eventos REACCION.
     *
     * @returns {void}
     */
    function attachUiListeners() {
        const btnPedir = document.getElementById('btn-pedir-cancion');
        if (btnPedir) {
            btnPedir.addEventListener('click', () => {
                const nicknameInput = document.getElementById('nickname');
                const tituloInput = document.getElementById('titulo-cancion');
                const formatoSelect = document.getElementById('formato-cancion');

                const nickname = nicknameInput ? nicknameInput.value.trim() : '';
                const titulo = tituloInput ? tituloInput.value.trim() : '';
                const formato = formatoSelect ? formatoSelect.value : 'mp3';

                if (!nickname || !titulo) {
                    writeLog('Debes ingresar un nickname y un título de canción.', 'error');
                    return;
                }

                currentNickname = nickname;
                currentSongId = titulo;

                writeLog(`Solicitando canción "${titulo}" en formato ${formato} para ${nickname}.`, 'success');

                // Inicia streaming gRPC-Web
                pedirCancion(titulo, formato);

                // Conecta WebSocket/STOMP para reacciones de esta canción
                connectReacciones();
            });
        }

        const reactionButtons = document.querySelectorAll('.reaction-btn');
        reactionButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                const tipoReaccion = btn.dataset.reaccion;
                enviarReaccion(tipoReaccion);
            });
        });
    }

    // Intentar auto-adjuntar cuando el DOM esté listo
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            attachAudioListeners();
            attachUiListeners();
        });
    } else {
        attachAudioListeners();
        attachUiListeners();
    }

    // Exponer funciones para uso manual o tests
    if (typeof window !== 'undefined') {
        window.__writeAudioLog = writeLog;
        window.__attachAudioListeners = attachAudioListeners;
    }
})();
