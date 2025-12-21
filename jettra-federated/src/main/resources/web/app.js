document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('login-form');
    const loginView = document.getElementById('login-view');
    const dashboardView = document.getElementById('dashboard-view');
    const logoutBtn = document.getElementById('logout-btn');
    const settingsBtn = document.getElementById('settings-btn');
    const refreshBtn = document.getElementById('refresh-btn');
    const loginError = document.getElementById('login-error');
    const passwordModal = document.getElementById('password-modal');
    const closeModal = document.getElementById('close-modal');
    const changePasswordForm = document.getElementById('change-password-form');
    const configBtn = document.getElementById('config-btn');
    const configSection = document.getElementById('config-section');
    const configNodeSelect = document.getElementById('config-node-select');
    const configEditorArea = document.getElementById('config-editor-area');
    const saveConfigBtn = document.getElementById('save-config-btn');
    const configStatusMsg = document.getElementById('config-status-msg');

    let updateInterval;
    let confirmModal, successModal;

    // Initialize Flowbite Modals
    setTimeout(() => {
        const $confirmModalEl = document.getElementById('generic-confirm-modal');
        const $successModalEl = document.getElementById('success-modal');

        if ($confirmModalEl && typeof Modal !== 'undefined') {
            confirmModal = new Modal($confirmModalEl);

            // Explicitly handle cancel buttons
            document.getElementById('cancel-confirm-modal').addEventListener('click', () => confirmModal.hide());
            document.getElementById('close-confirm-modal').addEventListener('click', () => confirmModal.hide());
        }

        if ($successModalEl && typeof Modal !== 'undefined') {
            successModal = new Modal($successModalEl);
        }
    }, 500);

    function showConfirmGeneric(text, onConfirm) {
        document.getElementById('confirm-modal-text').innerHTML = text;
        const confirmBtn = document.getElementById('confirm-modal-action-btn');

        // Remove old listeners by cloning
        const newConfirmBtn = confirmBtn.cloneNode(true);
        confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

        newConfirmBtn.addEventListener('click', () => {
            if (confirmModal) confirmModal.hide();
            // Give a small delay for the hide animation before starting the next action
            // or showing another modal.
            setTimeout(() => {
                onConfirm();
            }, 300);
        });

        if (confirmModal) confirmModal.show();
    }

    function showSuccess(message, autoClose = true, duration = 3000) {
        const msgEl = document.getElementById('success-message');
        const progressContainer = document.getElementById('success-progress-container');
        const progressBar = document.getElementById('success-progress-bar');
        const modalBtn = document.getElementById('success-modal-btn');

        msgEl.textContent = message;

        if (autoClose) {
            progressContainer.classList.remove('hidden');
            modalBtn.classList.add('hidden');
            progressBar.style.width = '0%';

            successModal.show();

            let start = null;
            const animate = (timestamp) => {
                if (!start) start = timestamp;
                const progress = timestamp - start;
                const percent = Math.min((progress / duration) * 100, 100);
                progressBar.style.width = percent + '%';

                if (progress < duration) {
                    requestAnimationFrame(animate);
                } else {
                    setTimeout(() => {
                        successModal.hide();
                        // Reset for next time
                        setTimeout(() => {
                            progressContainer.classList.add('hidden');
                            modalBtn.classList.remove('hidden');
                            progressBar.style.width = '0%';
                        }, 500);
                    }, 200);
                }
            };
            requestAnimationFrame(animate);
        } else {
            progressContainer.classList.add('hidden');
            modalBtn.classList.remove('hidden');
            successModal.show();
        }
    }

    // -- Authentication --

    // Security: Force login on application startup by clearing stored tokens
    localStorage.removeItem('fed_token');
    localStorage.removeItem('fed_user');

    if (localStorage.getItem('fed_token')) {
        showDashboard();
    }

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const user = document.getElementById('username').value;
        const pass = document.getElementById('password').value;

        try {
            const res = await fetch('/federated/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: user, password: pass })
            });

            if (res.ok) {
                const data = await res.json();
                localStorage.setItem('fed_token', data.token);
                localStorage.setItem('fed_user', user);
                showDashboard();
            } else {
                const err = await res.text();
                loginError.textContent = 'Credenciales inválidas: ' + err;
                loginError.classList.remove('hidden');
            }
        } catch (error) {
            loginError.textContent = 'Error al conectar con el servidor';
            loginError.classList.remove('hidden');
        }
    });

    logoutBtn.addEventListener('click', () => {
        localStorage.removeItem('fed_token');
        localStorage.removeItem('fed_user');
        clearInterval(updateInterval);
        dashboardView.classList.add('hidden');
        loginView.classList.remove('hidden');
    });

    // -- Settings / Password --

    settingsBtn.addEventListener('click', () => {
        passwordModal.classList.remove('hidden');
        passwordMsg.classList.add('hidden');
        document.getElementById('old-password').value = '';
        document.getElementById('new-password').value = '';
    });

    closeModal.addEventListener('click', () => {
        passwordModal.classList.add('hidden');
    });

    changePasswordForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const oldPassword = document.getElementById('old-password').value;
        const newPassword = document.getElementById('new-password').value;
        const username = localStorage.getItem('fed_user') || 'admin';

        try {
            const res = await fetch('/federated/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, oldPassword, newPassword })
            });

            const data = await res.json();
            if (res.ok) {
                passwordMsg.textContent = 'Contraseña actualizada correctamente';
                passwordMsg.className = 'msg success';
                passwordMsg.classList.remove('hidden');
                setTimeout(() => passwordModal.classList.add('hidden'), 2000);
            } else {
                passwordMsg.textContent = 'Error: ' + (data.message || 'Contraseña actual incorrecta');
                passwordMsg.className = 'msg error';
                passwordMsg.classList.remove('hidden');
            }
        } catch (error) {
            passwordMsg.textContent = 'Error de conexión';
            passwordMsg.className = 'msg error';
            passwordMsg.classList.remove('hidden');
        }
    });

    // -- Dashboard Logic --

    refreshBtn.addEventListener('click', updateStatus);

    function showDashboard() {
        loginView.classList.add('hidden');
        dashboardView.classList.remove('hidden');
        updateStatus();
        if (updateInterval) clearInterval(updateInterval);
        updateInterval = setInterval(updateStatus, 3000);
    }

    // -- Configuration Management --

    configBtn.addEventListener('click', () => {
        configSection.classList.toggle('hidden');
        if (!configSection.classList.contains('hidden')) {
            loadConfig();
        }
    });

    configNodeSelect.addEventListener('change', loadConfig);

    async function loadConfig() {
        const nodeId = configNodeSelect.value;
        const endpoint = nodeId === 'federated' ? '/federated/config' : `/federated/node-config/${nodeId}`;

        configEditorArea.value = 'Cargando...';
        try {
            const res = await fetch(endpoint, {
                headers: { 'Authorization': 'Bearer ' + localStorage.getItem('fed_token') }
            });
            if (res.ok) {
                const configText = await res.text();
                configEditorArea.value = configText;
            } else {
                configEditorArea.value = 'Error al cargar la configuración: ' + await res.text();
            }
        } catch (error) {
            configEditorArea.value = 'Error de conexión';
            console.error('Error connecting for config:', error);
        }
    }

    saveConfigBtn.addEventListener('click', () => handleSave(false));

    async function handleSave(restart) {
        const configText = configEditorArea.value;
        try {
            JSON.parse(configText); // Basic validation
        } catch (e) {
            showConfigMsg('JSON inválido: ' + e.message, 'error');
            return;
        }

        try {
            const nodeId = configNodeSelect.value;
            let url = nodeId === 'federated' ? '/federated/config' : `/federated/node-config/${nodeId}`;
            if (restart) url += (url.includes('?') ? '&' : '?') + 'restart=true';

            const res = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + localStorage.getItem('fed_token')
                },
                body: configText
            });

            if (res.ok) {
                showConfigMsg(restart ? 'Configuración guardada. Reiniciando...' : 'Configuración guardada correctamente', 'success');
            } else {
                showConfigMsg('Error al guardar: ' + await res.text(), 'error');
            }
        } catch (error) {
            showConfigMsg('Error de conexión', 'error');
        }
    }

    function showConfigMsg(text, type) {
        configStatusMsg.textContent = text;
        configStatusMsg.className = 'msg ' + type;
        configStatusMsg.classList.remove('hidden');
        setTimeout(() => configStatusMsg.classList.add('hidden'), 3000);
    }

    async function updateStatus() {
        try {
            const res = await fetch('/federated/status', {
                headers: { 'Authorization': 'Bearer ' + localStorage.getItem('fed_token') }
            });
            if (res.status === 401) {
                logoutBtn.click();
                return;
            }
            const data = await res.json();
            renderDashboard(data);
        } catch (error) {
            console.error('Error fetching status:', error);
        }
    }

    function renderDashboard(data) {
        // Stats
        const nodes = data.nodes || [];
        document.getElementById('total-nodes').textContent = nodes.length;
        document.getElementById('active-nodes').textContent = nodes.filter(n => n.status === 'ACTIVE').length;
        document.getElementById('raft-term').textContent = data.raftTerm || 0;

        // Raft State Badge
        const badge = document.getElementById('raft-state-badge');
        badge.textContent = data.raftState || 'UNKNOWN';
        badge.className = 'badge ' + (data.raftState || '');

        // Raft Details
        document.getElementById('raft-leader').textContent = data.raftLeaderId || 'Buscando...';

        // Nodes Table
        const list = document.getElementById('nodes-list');
        list.innerHTML = '';

        // Update config node select if hidden
        const currentSelectedNode = configNodeSelect.value;
        const currentOptions = Array.from(configNodeSelect.options).map(o => o.value);

        nodes.sort((a, b) => a.id.localeCompare(b.id)).forEach(node => {
            // Add to dropdown if not present
            if (!currentOptions.includes(node.id)) {
                const opt = document.createElement('option');
                opt.value = node.id;
                opt.textContent = `Nodo: ${node.id}`;
                configNodeSelect.appendChild(opt);
            }

            const isLeader = node.id === data.leaderId;
            const metrics = node.metrics || {};
            const isActive = node.status === 'ACTIVE';

            // Metrics: Set to zero if node is inactive
            const ramUsage = (isActive && metrics.ramUsage) ? `${metrics.ramUsage}%` : '0%';
            const cpuUsage = (isActive && metrics.cpuUsage) ? `${metrics.cpuUsage}%` : '0%';
            const diskUsage = (isActive && metrics.diskUsage) ? `${metrics.diskUsage}%` : '0%';
            const latency = (isActive && metrics.latency) ? `${metrics.latency}ms` : '0ms';

            const row = document.createElement('tr');
            row.innerHTML = `
                <td><span class="status-dot ${node.status}"></span> ${node.status}</td>
                <td class="code">${node.id} ${isLeader ? '<i class="fas fa-crown text-warning" title="Leader"></i>' : ''}</td>
                <td>${node.url}</td>
                <td><span class="node-role">${isLeader ? 'LEADER' : 'FOLLOWER'}</span></td>
                <td>${cpuUsage}</td>
                <td>${ramUsage}</td>
                <td>${diskUsage}</td>
                <td>${latency}</td>
                <td>${node.lastSeen ? new Date(node.lastSeen).toLocaleTimeString() : 'N/A'}</td>
                <td>
                    <div class="action-group">
                        <button class="btn-icon text-warning ${!isActive ? 'opacity-50 cursor-not-allowed' : ''}" 
                                onclick="${isActive ? `stopNode('${node.id}')` : ''}" 
                                title="${isActive ? 'Detener Nodo' : 'Nodo ya inactivo'}"
                                ${!isActive ? 'disabled' : ''}>
                            <i class="fas fa-stop-circle"></i>
                        </button>
                        <button class="btn-icon text-danger" onclick="removeNode('${node.id}')" title="Remover del Cluster">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    </div>
                </td>
            `;
            list.appendChild(row);
        });

        // Peers
        const peersUl = document.getElementById('raft-peers');
        peersUl.innerHTML = '';
        (data.raftPeers || []).forEach(peer => {
            const li = document.createElement('li');
            li.textContent = peer;
            peersUl.appendChild(li);
        });

        document.getElementById('last-update').textContent = `Actualizado: ${new Date().toLocaleTimeString()}`;
    }

    window.stopNode = async (nodeId) => {
        const msg = `¿Está seguro de que desea detener el nodo <span class="font-bold text-primary">${nodeId}</span>?`;
        showConfirmGeneric(msg, async () => {
            try {
                const res = await fetch(`/federated/node/stop/${nodeId}`, {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('fed_token') }
                });
                if (res.ok) {
                    showSuccess(`Comando de detención enviado al nodo ${nodeId}`);
                    updateStatus();
                }
            } catch (e) {
                console.error('Error sending stop command:', e);
            }
        });
    };

    window.removeNode = async (nodeId) => {
        const msg = `¿Está seguro de que desea remover el nodo <span class="font-bold text-danger">${nodeId}</span> del cluster federado?`;
        showConfirmGeneric(msg, async () => {
            try {
                const res = await fetch(`/federated/node/remove/${nodeId}`, {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('fed_token') }
                });
                if (res.ok) {
                    showSuccess(`Nodo ${nodeId} removido del cluster federado`);
                    // Remove from select if present
                    const opt = configNodeSelect.querySelector(`option[value="${nodeId}"]`);
                    if (opt) opt.remove();
                    updateStatus();
                }
            } catch (e) {
                console.error('Error removing node:', e);
            }
        });
    };
});
