// funcion.js - wrapper simple para llamar a la implementación del streaming

function pedirCancion(titulo, formato) {
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

// Export para compatibilidad con CommonJS (no afecta al navegador)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { pedirCancion };
}

// ----------------------
// Estado global cliente
// ----------------------

let stompClient = null;
let currentSongId = null;
let currentNickname = null;
let currentSubscription = null;

// ----------------------
// Helpers UI
// ----------------------

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

function showReactionBubble(text) {
    const overlay = document.getElementById('reactions-overlay');
    if (!overlay) return;

    const bubble = document.createElement('div');
    bubble.className = 'reaction-bubble';
    bubble.textContent = text;

    const randomLeft = 20 + Math.random() * 60;
    bubble.style.left = randomLeft + '%';

    overlay.appendChild(bubble);

    setTimeout(() => {
        if (overlay.contains(bubble)) {
            overlay.removeChild(bubble);
        }
    }, 3000);
}

function updateUserListFromEvent(nickname, tipo) {
    const ul = document.getElementById('usuarios-lista');
    if (!ul || !nickname) return;

    if (tipo === 'PLAY') {
        const exists = Array.from(ul.children).some(li => li.dataset.user === nickname);
        if (!exists) {
            const li = document.createElement('li');
            li.dataset.user = nickname;
            li.textContent = nickname;
            li.classList.add('user-event');
            ul.appendChild(li);
        }
    } else if (tipo === 'PAUSE') {
        Array.from(ul.children).forEach(li => {
            if (li.dataset.user === nickname) {
                ul.removeChild(li);
            }
        });
    }
}

/**
 * Muestra una notificación "toast" en la esquina de la pantalla.
 * Se utiliza para mostrar mensajes privados del servidor (ej. errores de pago).
 */
function showPrivateNotification({ tipo, titulo, mensaje }) {
    const container = document.querySelector('.app-container');
    if (!container) return;

    const notification = document.createElement('div');
    notification.className = 'private-notification';

    // Añade una clase de estilo basada en el tipo de notificación
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

    // La notificación se elimina automáticamente después de 5 segundos
    setTimeout(() => {
        if (container.contains(notification)) {
            container.removeChild(notification);
        }
    }, 5000);
}

// ----------------------
// WebSocket / STOMP
// ----------------------
function connectReacciones() {
    if (!currentNickname || !currentSongId) {
        writeLog('No se puede conectar a reacciones: falta nickname o id de canción.', 'error');
        return;
    }

    // Desconectar si ya existe una conexión para evitar duplicados
    if (stompClient && stompClient.connected) {
        if (currentSubscription) {
            currentSubscription.unsubscribe();
            currentSubscription = null;
        }
        // La desconexión es asíncrona, así que conectamos en el callback
        stompClient.disconnect(() => {
            writeLog('Conexión anterior de reacciones cerrada.');
            procederConNuevaConexion();
        });
    } else {
        procederConNuevaConexion();
    }
}

function procederConNuevaConexion() {
    // Esto asegura que la información del usuario esté disponible durante el handshake inicial.
    const url = `http://localhost:5000/ws?nickname=${encodeURIComponent(currentNickname)}`;
    console.log('Conectando a SockJS con URL:', url);

    const socket = new SockJS(url);
    stompClient = Stomp.over(socket);
    // ================================================================

    stompClient.debug = (str) => {
        console.log('STOMP DEBUG:', str);
    };

    // La cabecera 'login' ya no es estrictamente necesaria para la identificación,
    // pero la dejamos por si es útil para otros interceptores.
    const headers = {
        login: currentNickname
    };

    stompClient.connect(headers, () => {
        console.log('CONEXIÓN STOMP EXITOSA. Suscribiendo a canales...');
        writeLog('Conectado al servidor de reacciones.', 'success');

        // (El resto de la función no cambia)
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

function manejarMensajeReaccion(data) {
    const { nickname, idCancion, tipo, contenido } = data;
    if (!idCancion || idCancion !== currentSongId) {
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

function enviarMensajeStomp(destino, payload) {
    if (!stompClient || !stompClient.connected) {
        writeLog('No hay conexión activa con el servidor de reacciones.', 'error');
        return;
    }
    stompClient.send(destino, {}, JSON.stringify(payload));
}

function enviarPlay() {
    if (!currentNickname || !currentSongId) return;
    enviarMensajeStomp('/app/reproducir', {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'PLAY',
        contenido: null
    });
}

function enviarPause() {
    if (!currentNickname || !currentSongId) return;
    enviarMensajeStomp('/app/detener', {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'PAUSE',
        contenido: null
    });
}

function enviarReaccion(tipoReaccion) {
    if (!currentNickname || !currentSongId) {
        writeLog('No se puede enviar reacción: falta nickname o canción.', 'error');
        return;
    }
    enviarMensajeStomp('/app/reaccionar', {
        nickname: currentNickname,
        idCancion: currentSongId,
        tipo: 'REACCION',
        contenido: tipoReaccion
    });
}

// ----------------------
// Listeners de audio y UI
// ----------------------

function attachAudioListeners() {
    const audio = document.getElementById('audio-player');
    if (!audio) {
        writeLog('No se encontró el elemento audio#audio-player.', 'error');
        return;
    }

    audio.addEventListener('play', function () {
        writeLog('Reproducción iniciada (play).', 'success');
        enviarPlay();
    });

    audio.addEventListener('pause', function () {
        writeLog('Reproducción pausada (pause).', 'error');
        enviarPause();
    });
}

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

            pedirCancion(titulo, formato);
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

// Auto‑registro al cargar DOM
(function () {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            attachAudioListeners();
            attachUiListeners();
        });
    } else {
        attachAudioListeners();
        attachUiListeners();
    }
})();

// ----------------------
// Exportar a window
// ----------------------
if (typeof window !== 'undefined') {
    window.pedirCancion = pedirCancion;
}