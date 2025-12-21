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
    const passwordMsg = document.getElementById('password-msg');

    let updateInterval;

    // -- Authentication --

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

        nodes.sort((a, b) => a.id.localeCompare(b.id)).forEach(node => {
            const isLeader = node.id === data.leaderId;
            const row = document.createElement('tr');
            row.innerHTML = `
                <td><span class="status-dot ${node.status}"></span> ${node.status}</td>
                <td class="code">${node.id} ${isLeader ? '<i class="fas fa-crown text-warning" title="Leader"></i>' : ''}</td>
                <td>${node.url}</td>
                <td><span class="node-role">${isLeader ? 'LEADER' : 'FOLLOWER'}</span></td>
                <td>${node.lastSeen ? new Date(node.lastSeen).toLocaleTimeString() : 'N/A'}</td>
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
});
