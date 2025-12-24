const App = {
    state: {
        token: localStorage.getItem('jettra_token'),
        currentDb: null,
        currentCol: null,
        docs: [],
        viewMode: 'table', // table, json, tree
        searchTerm: '',
        // Pagination
        currentPage: 1,
        pageSize: 10,
        hasMore: false,
        clusterInterval: null
    },

    showNotification(message, type = 'info', title = null) {
        const container = document.getElementById('notification-container');
        if (!container) return; // Should not happen

        const notif = document.createElement('div');
        notif.className = `notification ${type}`;

        // Icon based on type
        let icon = 'ℹ️';
        if (type === 'success') icon = '✅';
        if (type === 'error') icon = '❌';
        if (type === 'warning') icon = '⚠️';

        if (!title) {
            title = type.charAt(0).toUpperCase() + type.slice(1);
        }

        notif.innerHTML = `
            <div style="font-size: 1.2rem;">${icon}</div>
            <div class="notification-content">
                <span class="notification-title">${title}</span>
                <p style="margin:0; opacity: 0.9;">${message}</p>
            </div>
            <button class="notification-close" onclick="this.parentElement.remove()">×</button>
        `;

        container.appendChild(notif);

        // Auto remove after 5s
        setTimeout(() => {
            notif.style.animation = 'fadeOut 0.3s ease-out forwards';
            setTimeout(() => {
                if (notif.parentElement) notif.remove();
            }, 300);
        }, 5000);
    },

    init() {
        // Theme Init
        if (localStorage.getItem('jettra_theme') === 'light') {
            document.body.classList.add('light-theme');
        }

        if (this.state.token) {
            this.showDashboard();

            // Restore role
            const storedRole = localStorage.getItem('jettra_role');
            if (storedRole) this.state.role = storedRole;
        } else {
            this.showLogin();
        }
        this.bindEvents();
    },

    bindEvents() {
        // Theme
        document.getElementById('theme-toggle-btn').addEventListener('click', () => this.toggleTheme());

        // Auth
        document.getElementById('login-form').addEventListener('submit', (e) => { e.preventDefault(); this.handleLogin(); });
        document.getElementById('logout-btn').addEventListener('click', () => this.logout());

        // Show Users menu if admin
        const usersMenu = document.getElementById('menu-users');
        if (usersMenu) {
            if (this.state.role === 'admin') {
                usersMenu.classList.remove('hidden');
            } else {
                usersMenu.classList.add('hidden');
            }
        }

        // Navigation / Actions
        document.getElementById('refresh-btn').addEventListener('click', () => this.refresh());
        document.getElementById('create-db-btn').addEventListener('click', () => this.promptCreateDatabase());
        document.getElementById('change-pass-btn').addEventListener('click', () => this.openPasswordView());

        // Document Actions
        document.getElementById('add-doc-btn').addEventListener('click', () => this.openDocEditor());
        document.getElementById('doc-search').addEventListener('input', (e) => {
            this.state.searchTerm = e.target.value.toLowerCase();
            this.state.currentPage = 1; // Reset page on search
            this.loadDocuments(this.state.currentDb, this.state.currentCol);
        });
        document.getElementById('view-mode').addEventListener('change', (e) => {
            this.state.viewMode = e.target.value;
            this.renderDocuments();
        });

        // Sidebar Toggle
        const menuToggle = document.getElementById('menu-toggle');
        if (menuToggle) {
            menuToggle.addEventListener('click', () => {
                document.querySelector('.sidebar').classList.toggle('open');
            });
        }

        // Close Sidebar on nav click (mobile)
        document.querySelectorAll('.sidebar-nav').forEach(nav => {
            nav.addEventListener('click', (e) => {
                if (window.innerWidth <= 768) {
                    document.querySelector('.sidebar').classList.remove('open');
                }
            });
        });
    },

    updateUsersMenuVisibility() {
        const adminMenus = ['menu-users', 'menu-config', 'menu-federated'];
        adminMenus.forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                if (this.state.role === 'admin') {
                    el.classList.remove('hidden');
                } else {
                    el.classList.add('hidden');
                }
            }
        });
    },

    refresh() {
        if (this.state.currentDb && this.state.currentCol) {
            this.loadDocuments(this.state.currentDb, this.state.currentCol);
        }
        this.loadDatabases();

        // Refresh cluster status if in cluster section
        const clusterSection = document.getElementById('cluster-section');
        if (clusterSection && !clusterSection.classList.contains('hidden')) {
            this.refreshClusterStatus();
        }
    },

    toggleTheme() {
        document.body.classList.toggle('light-theme');
        const theme = document.body.classList.contains('light-theme') ? 'light' : 'dark';
        localStorage.setItem('jettra_theme', theme);
    },

    // --- Auth ---
    async handleLogin() {
        const user = document.getElementById('username').value;
        const pass = document.getElementById('password').value;
        try {
            const res = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: user, password: pass })
            });

            if (res.ok) {
                const data = await res.json();
                if (!data.token) {
                    this.showNotification('Login successful but no token received', 'error');
                    return;
                }
                this.state.token = data.token;
                this.state.role = data.role; // Store role
                localStorage.setItem('jettra_token', data.token);
                // Also store role locally if we want persistent menu visibility, 
                // but security-wise backend checks are key.
                localStorage.setItem('jettra_role', data.role || '');

                this.showNotification('Login successful', 'success');
                document.getElementById('user-display').textContent = user;
                this.showDashboard();
                this.updateUsersMenuVisibility(); // Update menu visibility after login
            } else {
                const text = await res.text();
                this.showNotification('Login failed: ' + res.status + ' ' + text, 'error');
                document.getElementById('login-error').textContent = 'Invalid credentials';
            }
        } catch (e) {
            this.showNotification('Login error: ' + e.message, 'error');
            document.getElementById('login-error').textContent = 'Connection error';
        }
    },

    logout() {
        if (this.state.clusterInterval) {
            clearInterval(this.state.clusterInterval);
            this.state.clusterInterval = null;
        }
        this.state.token = null;
        this.state.currentDb = null;
        this.state.currentCol = null;
        this.state.role = null; // Clear role on logout
        localStorage.removeItem('jettra_token');
        localStorage.removeItem('jettra_role'); // Clear role from local storage
        this.showLogin();
        this.updateUsersMenuVisibility(); // Update menu visibility after logout
    },

    async restartInternalNode() {
        if (!confirm('¿Está seguro de que desea REINICIAR este nodo?')) return;

        this.showNotification('Iniciando reinicio del nodo...', 'warning');
        try {
            const res = await fetch('/api/cluster/restart', {
                method: 'POST',
                headers: { 'Authorization': this.state.token }
            });
            if (res.ok) {
                this.showNotification('Comando de reinicio aceptado. El servidor se reiniciará en breve.', 'success');
                // Optional: Logout or redirect after some time
                setTimeout(() => {
                    window.location.reload();
                }, 5000);
            } else {
                const text = await res.text();
                this.showNotification('Error al reiniciar: ' + text, 'error');
            }
        } catch (e) {
            this.showNotification('Error de conexión al solicitar reinicio', 'error');
        }
    },

    showLogin() {
        document.getElementById('login-view').classList.add('active');
        document.getElementById('dashboard-view').classList.remove('active');
    },

    showCluster() {
        this.hideAllViews();
        document.getElementById('nav-cluster').classList.add('active');
        document.getElementById('cluster-view').classList.add('active');
        this.loadCluster();
    },

    showBackups() {
        this.hideAllViews();
        document.getElementById('nav-backups').classList.add('active');
        document.getElementById('backups-view').classList.add('active');
        this.loadBackups();
    },

    async promptCreateBackup(preselectedDb = null) {
        try {
            const res = await this.authenticatedFetch('/api/dbs');
            const dbs = await res.json();

            this.renderCustomModal('Create Backup', `
                <div class="form-group">
                    <label>Select Database</label>
                    <select id="backup-db-select" class="form-control" style="width: 100%; padding: 0.5rem;">
                        ${dbs.map(d => `<option value="${d.name}" ${d.name === preselectedDb ? 'selected' : ''}>${d.name}</option>`).join('')}
                    </select>
                </div>
                <div class="form-group" style="margin-top: 1rem; color: #666; font-size: 0.9em;">
                    <label>Estimated Filename:</label>
                    <div id="backup-filename-preview" style="font-family: monospace; padding: 5px; background: #f5f5f5; border-radius: 4px;">...</div>
                </div>
            `, `
                <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
                <button class="btn btn-primary" id="modal-backup-btn">Backup & Download</button>
            `);

            const select = document.getElementById('backup-db-select');
            const preview = document.getElementById('backup-filename-preview');

            const updatePreview = () => {
                const db = select.value;
                const date = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
                preview.textContent = `${db}_${date}.zip`;
            };

            select.addEventListener('change', updatePreview);
            updatePreview(); // Init

            document.getElementById('modal-backup-btn').addEventListener('click', () => {
                const db = select.value;
                if (db) {
                    this.backupAndDownload(db);
                    this.closeInputModal();
                }
            });

            if (preselectedDb) {
                select.disabled = true; // Lock if preselected? Or let them change? Let's default to letting them change but selecting it.
                // User requirement "show the database" implied maybe just for that DB. But "select database" in create backup button implies choice.
                // If called from sidebar, maybe we lock it or just select it. Let's just select it.
            }

        } catch (e) {
            this.showNotification('Failed to load databases', 'error');
        }
    },

    backupAndDownload(db) {
        this.showNotification(`Creating backup for ${db}...`, 'info');
        this.authenticatedFetch('/api/backup?db=' + db, { method: 'POST' })
            .then(async res => {
                if (res.ok) {
                    const data = await res.json();
                    this.showNotification('Backup created. Downloading...', 'success');

                    // Use fetch to download with auth header to avoid Unauthorized errors
                    try {
                        const downloadRes = await this.authenticatedFetch(`/api/backup/download?file=${data.file}`);
                        if (downloadRes.ok) {
                            const blob = await downloadRes.blob();
                            const url = window.URL.createObjectURL(blob);
                            const a = document.createElement('a');
                            a.href = url;
                            a.download = data.file;
                            document.body.appendChild(a);
                            a.click();
                            window.URL.revokeObjectURL(url);
                            document.body.removeChild(a);

                            // Refresh list
                            const backupsSection = document.getElementById('backups-section');
                            if (backupsSection && !backupsSection.classList.contains('hidden')) {
                                this.loadBackups();
                            }
                        } else {
                            const text = await downloadRes.text();
                            this.showNotification('Download failed: ' + text, 'error');
                        }
                    } catch (e) {
                        this.showNotification('Download failed: ' + e.message, 'error');
                    }
                } else {
                    const errorDetails = await res.text();
                    this.showNotification('Backup failed: ' + errorDetails, 'error');
                }
            })
            .catch(e => {
                this.showNotification('Backup request failed: ' + e.message, 'error');
            });
    },

    promptBackupDatabase(db) {
        this.promptCreateBackup(db);
    },

    // Old loadDatabases removed


    async loadBackups() {
        try {
            const res = await this.authenticatedFetch('/api/backups');
            if (res.ok) {
                const files = await res.json();
                const tbody = document.getElementById('backups-list');
                tbody.innerHTML = '';
                files.forEach(f => {
                    const tr = document.createElement('tr');
                    // Parse date from filename: dbname_YYYYMMDDHHMMSS.zip
                    let dateStr = 'Unknown';
                    const parts = f.split('_');
                    if (parts.length > 1) {
                        const timePart = parts[parts.length - 1].replace('.zip', '');
                        if (timePart.length === 14) {
                            // YYYY-MM-DD HH:MM:SS
                            dateStr = timePart.substring(0, 4) + '-' + timePart.substring(4, 6) + '-' + timePart.substring(6, 8) + ' ' +
                                timePart.substring(8, 10) + ':' + timePart.substring(10, 12) + ':' + timePart.substring(12, 14);
                        }
                    }

                    tr.innerHTML = `
                        <td>${f}</td>
                        <td>${dateStr}</td>
                        <td class="actions-cell">
                            <button onclick="app.downloadBackup('${f}')">Download</button>
                            <button class="danger" onclick="app.promptRestore('${f}')">Restore</button>
                        </td>
                    `;
                    tbody.appendChild(tr);
                });
            }
        } catch (e) {
            this.showNotification('Failed to load backups', 'error');
        }
    },

    promptGeneralRestore() {
        this.renderCustomModal('Restore from File', `
            <div class="alert alert-warning" style="background: #fff3cd; color: #856404; padding: 10px; border-radius: 4px; margin-bottom: 1em;">
                <strong>⚠️ Warning:</strong> Restoring will overwrite the target database if it exists using the uploaded backup!
            </div>
            <div class="form-group">
                <label>Select Backup File (.zip)</label>
                <input type="file" id="restore-file-input" accept=".zip" class="form-control" style="width: 100%; padding: 0.5rem;">
            </div>
            <div class="form-group">
                <label>Target Database Name</label>
                <input type="text" id="restore-target-db-upload" placeholder="e.g. restored_db" class="form-control" style="width: 100%; padding: 0.5rem;">
            </div>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn danger" id="modal-upload-restore-btn">Upload & Restore</button>
        `);

        document.getElementById('modal-upload-restore-btn').addEventListener('click', () => {
            const fileInput = document.getElementById('restore-file-input');
            const targetDb = document.getElementById('restore-target-db-upload').value;

            if (fileInput.files.length === 0) {
                alert("Please select a file");
                return;
            }
            if (!targetDb) {
                alert("Please enter a target database name");
                return;
            }

            if (confirm(`Are you sure you want to restore to "${targetDb}"? Data will be overwritten.`)) {
                this.uploadAndRestore(fileInput.files[0], targetDb);
            }
        });
    },

    uploadAndRestore(file, targetDb) {
        this.showNotification('Uploading and Restoring...', 'info');
        // Need to send raw bytes or implement multipart. Backend expects raw bytes body for simplicity in current impl.
        // Reading file as ArrayBuffer
        const reader = new FileReader();
        reader.onload = async (e) => {
            const arrayBuffer = e.target.result;
            try {
                const res = await fetch(`/api/restore/upload?db=${targetDb}`, {
                    method: 'POST',
                    headers: {
                        'Authorization': this.state.token,
                        'Content-Type': 'application/octet-stream' // Or zip
                    },
                    body: arrayBuffer
                });

                if (res.ok) {
                    this.showNotification('Restore successful!', 'success');
                    this.closeInputModal();
                    this.loadBackups(); // Refresh list to maybe show new file if we listed it (we do save it to backups dir)
                    // Also refresh DB list if we are in dashboard?
                    // We might not be in dashboard view but backups view.
                } else {
                    const t = await res.text();
                    this.showNotification('Restore failed: ' + t, 'error');
                }
            } catch (err) {
                this.showNotification('Upload failed: ' + err.message, 'error');
            }
        };
        reader.readAsArrayBuffer(file);
    },

    async downloadBackup(file) {
        this.showNotification(`Downloading ${file}...`, 'info');
        try {
            const res = await this.authenticatedFetch(`/api/backup/download?file=${file}`);
            if (res.ok) {
                const blob = await res.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = file;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                this.showNotification('Download started', 'success');
            } else {
                const text = await res.text();
                this.showNotification('Download failed: ' + text, 'error');
            }
        } catch (e) {
            this.showNotification('Download error: ' + e.message, 'error');
        }
    },

    promptRestore(file) {
        // Parse proposed DB name from file
        let proposedName = '';
        const parts = file.split('_');
        if (parts.length > 1) {
            proposedName = parts.slice(0, parts.length - 1).join('_');
        }

        this.renderCustomModal('Restore Database', `
            <div class="alert alert-warning" style="background: #fff3cd; color: #856404; padding: 10px; border-radius: 4px; margin-bottom: 1em;">
                <strong>⚠️ Warning:</strong> Restoring will overwrite the target database if it exists !.
            </div>
            <div class="form-group">
                <label>Backup File:</label>
                <div style="font-weight: bold; margin-bottom: 10px;">${file}</div>
            </div>
            <div class="form-group">
                <label>Target Database Name</label>
                <input type="text" id="restore-target-db" value="${proposedName}" class="form-control" style="width: 100%; padding: 0.5rem;">
            </div>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn danger" id="modal-restore-btn">Restore Database</button>
        `);

        document.getElementById('modal-restore-btn').addEventListener('click', () => {
            const targetDb = document.getElementById('restore-target-db').value;
            if (targetDb) {
                if (confirm(`Are you sure you want to restore to "${targetDb}"? Data will be overwritten.`)) {
                    this.restoreBackup(file, targetDb);
                }
            }
        });
    },

    renderCustomModal(title, bodyHtml, footerHtml) {
        const overlay = document.getElementById('modal-overlay');
        document.getElementById('modal-title').textContent = title;
        document.getElementById('modal-body').innerHTML = bodyHtml;
        document.getElementById('modal-footer').innerHTML = footerHtml;
        overlay.classList.remove('hidden');
        overlay.style.display = 'flex';
    },

    restoreBackup(file, targetDb) {
        this.authenticatedFetch('/api/restore', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ file: file, db: targetDb })
        }).then(async res => {
            if (res.ok) {
                this.showNotification('Restore successful', 'success');
                this.closeInputModal();
                this.loadDatabases(); // Refresh DB list
            } else {
                const t = await res.text();
                this.showNotification('Restore failed: ' + t, 'error');
            }
        });
    },

    openPasswordView() {
        this.renderView('password');
    },

    showDashboard() {
        document.getElementById('login-view').classList.remove('active');
        document.getElementById('dashboard-view').classList.add('active');
        this.loadDatabases();
    },

    // --- DB/Col Operations ---
    promptCreateDatabase() {
        this.renderCustomModal('Create Database', `
            <div class="form-group">
                <label>Database Name</label>
                <input type="text" id="new-db-name" class="form-control" placeholder="my_database">
            </div>
            <div class="form-group">
                <label>Storage Engine</label>
                <select id="new-db-engine" class="form-control">
                    <option value="JettraBasicStore">Basic Store (Default, Standard JSON/CBOR)</option>
                    <option value="JettraEngineStore">Engine Store (Optimized Binary)</option>
                </select>
                <small class="text-muted">Choose 'Engine Store' for high performance with Java objects.</small>
            </div>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-primary" id="modal-create-db-btn">Create</button>
        `);

        document.getElementById('modal-create-db-btn').addEventListener('click', () => {
            const name = document.getElementById('new-db-name').value;
            const engine = document.getElementById('new-db-engine').value;

            if (!name) {
                this.showNotification('Database name is required', 'error');
                return;
            }

            this.authenticatedFetch('/api/dbs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name, engine: engine })
            }).then(async res => {
                if (res.ok) {
                    this.showNotification('Database created', 'success');
                    this.loadDatabases();
                    this.closeInputModal();
                } else {
                    res.text().then(t => this.showNotification('Failed to create DB: ' + t, 'error'));
                }
            });
        });
    },

    async changePassword(p1, p2) {
        if (p1 !== p2) {
            this.showNotification("Passwords do not match", 'warning');
            return;
        }

        try {
            const res = await this.authenticatedFetch('/api/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ newPassword: p1 })
            });
            if (res.ok) {
                this.showNotification("Password updated", 'success');
                this.refresh(); // Back to dashboard home
            } else {
                this.showNotification("Failed to update password", 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification("Error updating password", 'error');
        }
    },

    async authenticatedFetch(url, options = {}) {
        if (!options.headers) options.headers = {};
        options.headers['Authorization'] = this.state.token;

        const res = await fetch(url, options);
        if (res.status === 401) {
            this.logout();
            throw new Error('Unauthorized');
        }
        return res;
    },

    // --- Databases & Collections ---
    async loadDatabases() {
        try {
            const res = await this.authenticatedFetch('/api/dbs');
            const dbs = await res.json();
            this.renderDbTree(dbs);
        } catch (e) {
            console.error('Failed to load DBs', e);
        }
    },

    renderDbTree(dbs) {
        const list = document.getElementById('db-list');
        list.innerHTML = '';
        dbs.forEach(dbData => {
            const db = dbData.name;
            const engine = dbData.engine; // Backend now returns this correctly
            const li = document.createElement('li');

            const itemDiv = document.createElement('div');
            itemDiv.className = 'tree-item';
            // Added Info button
            itemDiv.innerHTML = `
                <span class="label">📂 ${db}</span>
                <div class="tree-actions">
                    <button class="tree-btn" title="Info" onclick="App.showDatabaseInfo('${db}', '${engine}')">ℹ️</button>
                    <button class="tree-btn" title="Create Collection" onclick="App.promptCreateCollection('${db}')">+</button>
                    <button class="tree-btn" title="Rename" onclick="App.promptRenameDatabase('${db}')">✎</button>
                    <button class="tree-btn" title="Backup" onclick="App.promptBackupDatabase('${db}')">💾</button>
                    <button class="tree-btn danger" title="Delete" onclick="App.confirmDeleteDatabase('${db}')">🗑</button>
                </div>
            `;

            const childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children';

            itemDiv.addEventListener('click', (e) => {
                if (e.target.tagName === 'BUTTON') return;
                // Toggle expand
                if (childrenUl.innerHTML === '') {
                    this.loadCollections(db, childrenUl);
                }
                childrenUl.classList.toggle('expanded');
            });

            li.appendChild(itemDiv);
            li.appendChild(childrenUl);
            list.appendChild(li);
        });
    },

    showDatabaseInfo(name, engine) {
        this.renderCustomModal('Database Information', `
            <div style="padding: 10px;">
                <div class="form-group">
                    <label style="font-weight:bold">Name:</label>
                    <div style="font-size: 1.1em; padding: 5px 0;">${name}</div>
                </div>
                <div class="form-group" style="margin-top: 15px;">
                    <label style="font-weight:bold">Storage Engine:</label>
                    <div style="padding: 5px 0;">
                        <span class="badge bg-primary" style="font-size: 1em; padding: 5px 10px;">${engine || 'Unknown'}</span>
                    </div>
                     <p style="margin-top:5px; color:#666; font-size:0.9em;">
                        ${engine === 'JettraEngineStore' ? 'Binary format optimized for performance.' : 'Standard JSON/CBOR storage.'}
                     </p>
                </div>
            </div>
        `, `
            <button class="btn btn-primary" onclick="app.closeInputModal()">Close</button>
        `);
    },

    confirmDeleteDatabase(name) {
        this.renderCustomModal('Delete Database', `
            <div class="alert alert-danger" style="background: #f8d7da; color: #721c24; padding: 15px; border-radius: 5px; margin-bottom: 20px;">
                <strong>⚠️ DANGER ZONE</strong>
                <p style="margin: 10px 0 0;">You are about to delete the database <strong>"${name}"</strong>.</p>
                <p style="margin: 5px 0 0;">This action cannot be undone. All collections and data will be permanently lost.</p>
            </div>
            <div class="form-group">
                <label>Type database name to confirm:</label>
                <input type="text" id="confirm-delete-db-input" class="form-control" placeholder="${name}" style="width: 100%; padding: 8px; margin-top:5px;">
            </div>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn danger" id="modal-confirm-delete-btn" disabled>Delete Database</button>
        `);

        const input = document.getElementById('confirm-delete-db-input');
        const btn = document.getElementById('modal-confirm-delete-btn');

        input.addEventListener('input', (e) => {
            if (e.target.value === name) {
                btn.disabled = false;
                btn.style.opacity = '1';
                btn.style.cursor = 'pointer';
            } else {
                btn.disabled = true;
                btn.style.opacity = '0.6';
                btn.style.cursor = 'not-allowed';
            }
        });

        btn.addEventListener('click', () => {
            this.authenticatedFetch(`/api/dbs?name=${name}`, { method: 'DELETE' })
                .then(() => {
                    this.showNotification(`Database ${name} deleted`, 'success');
                    this.closeInputModal();
                    this.loadDatabases();
                })
                .catch(e => this.showNotification('Failed to delete: ' + e, 'error'));
        });
    },

    async loadCollections(db, container) {
        try {
            const res = await this.authenticatedFetch(`/api/dbs/${db}/cols`);
            const cols = await res.json();
            container.innerHTML = '';
            cols.forEach(col => {
                const li = document.createElement('li');
                const itemDiv = document.createElement('div');
                itemDiv.className = 'tree-item';
                if (this.state.currentDb === db && this.state.currentCol === col) {
                    itemDiv.classList.add('active');
                }

                itemDiv.innerHTML = `
                    <span class="label">📄 ${col}</span>
                    <div class="tree-actions">
                         <button class="tree-btn" title="Rename" onclick="App.promptRenameCollection('${db}', '${col}')">✎</button>
                         <button class="tree-btn danger" title="Delete" onclick="App.confirmDeleteCollection('${db}', '${col}')">🗑</button>
                    </div>
                `;

                itemDiv.addEventListener('click', (e) => {
                    if (e.target.tagName === 'BUTTON') return;
                    this.selectCollection(db, col);
                    // Update active state visual
                    document.querySelectorAll('.tree-item').forEach(el => el.classList.remove('active'));
                    itemDiv.classList.add('active');
                });

                li.appendChild(itemDiv);
                container.appendChild(li);
            });
        } catch (e) {
            console.error(e);
        }
    },



    promptRenameDatabase(oldName) {
        this.renderSimpleForm(`Rename ${oldName}`, oldName, 'Rename', (newName) => {
            if (!newName || newName === oldName) return;
            this.authenticatedFetch('/api/dbs/rename', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ oldName, newName })
            }).then(res => {
                if (res.ok) {
                    this.showNotification('Database renamed successfully', 'success');
                    this.closeInputModal();
                    this.loadDatabases();
                } else {
                    this.showNotification('Failed to rename database', 'error');
                }
            });
        });
    },





    promptCreateCollection(db) {
        this.renderSimpleForm('New Collection Name', '', 'Create Collection', (name) => {
            if (!name) return;
            this.authenticatedFetch('/api/cols', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ database: db, collection: name })
            }).then(() => {
                this.loadDatabases();
                // Fix: Close modal and notify
                this.closeInputModal();
                this.showNotification('Collection created', 'success');
                // document.getElementById('content-area').innerHTML = `<div class="empty-state"><h2>Collection Created</h2></div>`; // Optional
            });
        });
    },

    promptRenameCollection(db, oldName) {
        this.renderSimpleForm(`Rename ${oldName}`, oldName, 'Rename', (newName) => {
            if (!newName || newName === oldName) return;
            this.authenticatedFetch('/api/cols/rename', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ database: db, oldName, newName })
            }).then(() => {
                this.closeInputModal();
                this.loadDatabases();
            });
        });
    },

    confirmDeleteCollection(db, col) {
        if (confirm(`Delete collection '${col}'?`)) {
            this.authenticatedFetch(`/api/cols?database=${db}&collection=${col}`, { method: 'DELETE' })
                .then(() => {
                    if (this.state.currentCol === col && this.state.currentDb === db) {
                        this.state.currentCol = null;
                        document.getElementById('collection-actions').style.display = 'none';
                        document.getElementById('dashboard-section').innerHTML = '';
                    }
                    this.loadDatabases();
                });
        }
    },

    // --- Documents ---
    selectCollection(db, col) {
        this.showSection('dashboard');
        this.state.currentDb = db;
        this.state.currentCol = col;
        this.state.currentPage = 1;
        document.getElementById('breadcrumbs').textContent = `${db} / ${col}`;
        document.getElementById('collection-actions').style.display = 'flex';
        this.loadDocuments(db, col);
    },

    async loadDocuments(db, col) {
        try {
            const limit = this.state.pageSize;
            const offset = (this.state.currentPage - 1) * limit;

            // Note: Request limit+1 to check if there is a next page
            const res = await this.authenticatedFetch(`/api/query?db=${db}&col=${col}&limit=${limit + 1}&offset=${offset}`);
            let docs = await res.json();

            // Client-side search filtering if implemented (Ideally server-side but for now simplest)
            if (this.state.searchTerm) {
                docs = docs.filter(d => JSON.stringify(d).toLowerCase().includes(this.state.searchTerm));
            }

            if (docs.length > limit) {
                this.state.hasMore = true;
                docs.pop(); // Remove the extra check item
            } else {
                this.state.hasMore = false;
            }

            this.state.docs = docs;
            this.renderDocuments();
        } catch (e) {
            console.error(e);
        }
    },

    renderDocuments() {
        const container = document.getElementById('dashboard-section');
        const docs = this.state.docs;

        if (docs.length === 0 && this.state.currentPage === 1) {
            container.innerHTML = '<div class="empty-state"><p>No documents found</p></div>';
            return;
        }

        const mode = this.state.viewMode;
        let html = '';

        if (mode === 'table') {
            // Dynamic Columns
            const keys = new Set();
            // Always put ID first
            keys.add('_id');
            keys.add('id');

            docs.forEach(d => Object.keys(d).forEach(k => keys.add(k)));
            const columns = Array.from(keys).filter(k => k !== 'id' && k !== '_id'); // Clean ID dupes if any
            columns.unshift('ID'); // Display label

            html = '<div class="table-container"><table class="data-table"><thead><tr>';
            columns.forEach(k => html += `<th>${k}</th>`);
            html += '<th>Actions</th></tr></thead><tbody>';

            docs.forEach(doc => {
                const id = doc._id || doc.id;
                html += '<tr>';
                columns.forEach(k => {
                    let val = '';
                    if (k === 'ID') val = id;
                    else val = doc[k] !== undefined ? doc[k] : '';

                    if (typeof val === 'object') val = '[Object]'; // Simplify objects in table
                    html += `<td>${val}</td>`;
                });
                html += `<td>
                        <button class="btn btn-icon" onclick="App.openDocEditor('${id}')">✎</button>
                        <button class="btn btn-icon" onclick="App.showVersions('${id}')" title="History">🕒</button>
                        <button class="btn btn-icon" onclick="App.deleteDocument('${id}')">🗑</button>
                    </td></tr>`;
            });
            html += '</tbody></table></div>';

            // Pagination Controls
            html += `<div class="pagination">
                <button class="btn btn-sm btn-secondary" ${this.state.currentPage === 1 ? 'disabled' : ''} onclick="App.prevPage()">Previous</button>
                <span>Page ${this.state.currentPage}</span>
                <button class="btn btn-sm btn-secondary" ${!this.state.hasMore ? 'disabled' : ''} onclick="App.nextPage()">Next</button>
            </div>`;

        } else if (mode === 'json') {
            html = `<div class="json-view">${JSON.stringify(docs, null, 2)}</div>`;
            // Pagination for JSON view too?
            html += `<div class="pagination">
                <button class="btn btn-sm btn-secondary" ${this.state.currentPage === 1 ? 'disabled' : ''} onclick="App.prevPage()">Previous</button>
                <span>Page ${this.state.currentPage}</span>
                <button class="btn btn-sm btn-secondary" ${!this.state.hasMore ? 'disabled' : ''} onclick="App.nextPage()">Next</button>
            </div>`;
        } else if (mode === 'tree') {
            // Simple recursive tree render
            html = '<div class="doc-tree">';
            docs.forEach(doc => {
                html += this.renderJsonTree(doc);
                html += `<div style="margin-left: 1rem; margin-bottom: 1rem;">
                            <button class="btn btn-xs btn-primary" onclick="App.openDocEditor('${doc._id || doc.id}')">Edit</button>
                            <button class="btn btn-xs btn-ghost" onclick="App.deleteDocument('${doc._id || doc.id}')">Delete</button>
                          </div><hr style="border-color: var(--border-color)">`;
            });
            html += '</div>';
            html += `<div class="pagination">
                <button class="btn btn-sm btn-secondary" ${this.state.currentPage === 1 ? 'disabled' : ''} onclick="App.prevPage()">Previous</button>
                <span>Page ${this.state.currentPage}</span>
                <button class="btn btn-sm btn-secondary" ${!this.state.hasMore ? 'disabled' : ''} onclick="App.nextPage()">Next</button>
            </div>`;
        }

        container.innerHTML = html;
        window.App = this;
    },

    prevPage() {
        if (this.state.currentPage > 1) {
            this.state.currentPage--;
            this.loadDocuments(this.state.currentDb, this.state.currentCol);
        }
    },

    nextPage() {
        if (this.state.hasMore) {
            this.state.currentPage++;
            this.loadDocuments(this.state.currentDb, this.state.currentCol);
        }
    },

    renderJsonTree(obj) {
        // Rudimentary tree
        let html = `<div class="doc-tree-node">`;
        if (typeof obj === 'object' && obj !== null) {
            for (let k in obj) {
                html += `<div><strong style="color: var(--accent-color)">${k}:</strong> `;
                if (typeof obj[k] === 'object') {
                    html += this.renderJsonTree(obj[k]);
                } else {
                    html += `<span style="color: var(--text-secondary)">${obj[k]}</span>`;
                }
                html += `</div>`;
            }
        } else {
            html += `<span>${obj}</span>`;
        }
        html += `</div>`;
        return html;
    },

    // --- View Router & Render Helpers ---
    renderView(viewType, data = {}) {
        const container = document.getElementById('content-area');

        // Hide collection actions if not in 'list' view
        if (viewType !== 'list') {
            document.getElementById('collection-actions').style.display = 'none';
        }

        if (viewType === 'password') {
            container.innerHTML = `
                <div class="center-view-container">
                    <div class="center-view-header">
                        <h3>Change Password</h3>
                    </div>
                    <form id="center-pass-form">
                        <div class="form-group">
                            <label>New Password</label>
                            <input type="password" id="center-new-pass" required>
                        </div>
                        <div class="form-group">
                            <label>Confirm Password</label>
                            <input type="password" id="center-confirm-pass" required>
                        </div>
                        <div class="center-view-footer">
                             <button type="button" class="btn btn-secondary" onclick="App.refresh()">Cancel</button>
                             <button type="submit" class="btn btn-primary">Save Password</button>
                        </div>
                    </form>
                </div>
            `;
            document.getElementById('center-pass-form').addEventListener('submit', (e) => {
                e.preventDefault();
                this.changePassword(
                    document.getElementById('center-new-pass').value,
                    document.getElementById('center-confirm-pass').value
                );
            });
        }
    },

    renderSimpleForm(title, initialValue, confirmLabel, callback) {
        const overlay = document.getElementById('modal-overlay');
        const mTitle = document.getElementById('modal-title');
        const mBody = document.getElementById('modal-body');
        const mFooter = document.getElementById('modal-footer');

        mTitle.textContent = title;
        mBody.innerHTML = `
            <div class="form-group">
                <label>Name</label>
                <input type="text" id="modal-input-value" value="${initialValue}" class="form-control" style="width: 100%; padding: 0.5rem;">
            </div>
        `;
        mFooter.innerHTML = `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-primary" id="modal-confirm-btn">${confirmLabel}</button>
        `;

        document.getElementById('modal-confirm-btn').addEventListener('click', () => {
            const val = document.getElementById('modal-input-value').value;
            callback(val);
        });

        overlay.classList.remove('hidden');
        overlay.style.display = 'flex'; // Ensure flex display
    },

    showInputModal(title, bodyHtml, confirmCallback, confirmLabel = 'Save') {
        const overlay = document.getElementById('modal-overlay');
        const mTitle = document.getElementById('modal-title');
        const mBody = document.getElementById('modal-body');
        const mFooter = document.getElementById('modal-footer');

        document.querySelector('.modal').style.maxWidth = '500px'; // Default width

        mTitle.textContent = title;
        mBody.innerHTML = bodyHtml;
        mFooter.innerHTML = `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-primary" id="modal-confirm-btn">${confirmLabel}</button>
        `;

        document.getElementById('modal-confirm-btn').addEventListener('click', () => {
            confirmCallback();
        });

        overlay.classList.remove('hidden');
        overlay.style.display = 'flex';
    },

    closeInputModal() {
        const overlay = document.getElementById('modal-overlay');
        overlay.classList.add('hidden');
        overlay.style.display = 'none';
        document.getElementById('modal-body').innerHTML = ''; // Clean up
        document.querySelector('.modal').style.maxWidth = '500px'; // Reset width
    },

    openDocEditor(id = null) {
        // Doc Editor is complex, maybe keep it full page or use modal?
        // Let's use the modal but wide
        const overlay = document.getElementById('modal-overlay');
        const mTitle = document.getElementById('modal-title');
        const mBody = document.getElementById('modal-body');
        const mFooter = document.getElementById('modal-footer');

        document.querySelector('.modal').style.maxWidth = '800px'; // Widen for editor

        let initialContent = '{\n  \n}';
        let isEdit = false;

        if (id) {
            const doc = this.state.docs.find(d => (d._id || d.id) === id);
            if (doc) {
                initialContent = JSON.stringify(doc, null, 2);
                isEdit = true;
            }
        }

        mTitle.textContent = isEdit ? 'Edit Document' : 'New Document';
        mBody.innerHTML = `
             <div class="form-group">
                <label>ID (Optional for new)</label>
                <input type="text" id="modal-doc-id" value="${id || ''}" ${isEdit ? 'disabled' : ''} style="width: 100%; padding: 0.5rem; margin-bottom: 1rem;">
            </div>
            <div class="form-group" style="height: 400px;">
                <label>Content (JSON)</label>
                <textarea id="modal-doc-content"
                    style="width: 100%; height: 100%; background: var(--bg-primary); color: var(--text-primary); border: 1px solid var(--border-color); padding: 10px; font-family: monospace;">${initialContent}</textarea>
            </div>
        `;

        mFooter.innerHTML = `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-primary" id="modal-save-doc">Save Document</button>
        `;

        document.getElementById('modal-save-doc').addEventListener('click', () => {
            this.saveDocumentFromCenter(document.getElementById('modal-doc-id').value, document.getElementById('modal-doc-content').value);
        });

        overlay.classList.remove('hidden');
        overlay.style.display = 'flex';
    },

    async saveDocumentFromCenter(id, content) {
        let doc;
        try {
            doc = JSON.parse(content);
        } catch (e) {
            this.showNotification("Invalid JSON", 'error');
            return;
        }

        try {
            let res;
            if (this.state.docs.some(d => (d._id || d.id) === id)) {
                res = await this.authenticatedFetch(`/api/doc?db=${this.state.currentDb}&col=${this.state.currentCol}&id=${id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc)
                });
            } else {
                res = await this.authenticatedFetch(`/api/doc?db=${this.state.currentDb}&col=${this.state.currentCol}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc)
                });
            }

            if (res.ok) {
                // Return to list
                this.selectCollection(this.state.currentDb, this.state.currentCol);
                this.closeInputModal();
            } else {
                const text = await res.text();
                this.showNotification('Failed to save: ' + text, 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error saving document: ' + e.message, 'error');
        }
    },

    async deleteDocument(id) {
        const overlay = document.getElementById('modal-overlay');
        const title = document.getElementById('modal-title');
        const body = document.getElementById('modal-body');
        const footer = document.getElementById('modal-footer');

        title.textContent = 'Confirm Deletion';
        body.innerHTML = `
            <div style="text-align: center; padding: 1rem;">
                <p style="font-size: 1.1rem; color: var(--text-primary); margin-bottom: 1rem;">Are you sure you want to delete this document?</p>
                <div style="font-family: monospace; background: var(--bg-secondary); padding: 0.5rem; border-radius: 4px; color: var(--text-warning); margin-bottom: 1rem;">ID: ${id}</div>
                <p style="font-size: 0.9rem; color: var(--text-muted);">This action cannot be undone.</p>
            </div>
        `;

        footer.innerHTML = `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-danger" id="confirm-delete-btn">Delete Permanently</button>
        `;

        overlay.classList.remove('hidden');
        overlay.style.display = 'flex';

        document.getElementById('confirm-delete-btn').onclick = async () => {
            try {
                // Show loading state
                document.getElementById('confirm-delete-btn').textContent = 'Deleting...';
                document.getElementById('confirm-delete-btn').disabled = true;

                const res = await this.authenticatedFetch(`/api/doc?db=${this.state.currentDb}&col=${this.state.currentCol}&id=${id}`, {
                    method: 'DELETE'
                });

                if (res.ok) {
                    this.showNotification('Document deleted successfully', 'success');
                    this.loadDocuments(this.state.currentDb, this.state.currentCol);
                    this.closeInputModal();
                } else {
                    const text = await res.text();
                    this.showNotification('Failed to delete: ' + text, 'error');
                    this.closeInputModal(); // Close anyway or keep open? keep open allows retry, but improved UX usually closes or resets
                }
            } catch (e) {
                console.error(e);
                this.showNotification('Error deleting document: ' + e.message, 'error');
                this.closeInputModal();
            }
        };
    },

    // --- Versioning ---
    async showVersions(id) {
        this.renderCustomModal('Document History', 'Loading versions...', '');

        try {
            const res = await this.authenticatedFetch(`/api/versions?db=${this.state.currentDb}&col=${this.state.currentCol}&id=${id}`);
            if (res.ok) {
                const versions = await res.json();

                let html = `
                    <div style="margin-bottom: 1rem;">
                        <strong>Document ID:</strong> <span class="font-mono">${id}</span>
                    </div>
                `;

                if (versions.length === 0) {
                    html += '<div class="alert alert-info" style="background:var(--bg-secondary); padding:1rem;">No history found for this document.</div>';
                } else {
                    html += `
                    <div class="table-container" style="max-height: 300px; overflow-y: auto;">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>Timestamp</th>
                                    <th>Date</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                    `;

                    versions.forEach(ts => {
                        const date = new Date(parseInt(ts)).toLocaleString();
                        html += `
                            <tr>
                                <td class="font-mono">${ts}</td>
                                <td>${date}</td>
                                <td>
                                    <div class="flex space-x-2" style="display:flex; gap: 0.5rem;">
                                        <button class="btn btn-sm btn-info" onclick="App.viewVersionContent('${id}', '${ts}')">View</button>
                                        <button class="btn btn-sm btn-secondary" onclick="App.restoreVersion('${id}', '${ts}')">Restore</button>
                                    </div>
                                </td>
                            </tr>
                        `;
                    });

                    html += `</tbody></table></div>`;
                }

                this.renderCustomModal('Document History', html,
                    '<button class="btn btn-secondary" onclick="app.closeInputModal()">Close</button>'
                );
            } else {
                this.renderCustomModal('Document History', '<div class="text-red-500">Failed to load versions</div>',
                    '<button class="btn btn-secondary" onclick="app.closeInputModal()">Close</button>'
                );
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error loading versions', 'error');
            this.closeInputModal();
        }
    },

    async restoreVersion(id, version) {
        if (!confirm(`Restore document to version from ${new Date(parseInt(version)).toLocaleString()}? Current state will be saved as a new version.`)) return;

        try {
            const res = await this.authenticatedFetch('/api/restore-version', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    db: this.state.currentDb,
                    col: this.state.currentCol,
                    id: id,
                    version: version
                })
            });

            if (res.ok) {
                this.showNotification('Document restored successfully', 'success');
                this.closeInputModal();
                this.loadDocuments(this.state.currentDb, this.state.currentCol);
            } else {
                const text = await res.text();
                this.showNotification('Restore failed: ' + text, 'error');
            }
        } catch (e) {
            this.showNotification('Error restoring version: ' + e.message, 'error');
        }
    },

    async viewVersionContent(id, version) {
        this.renderCustomModal('Version Content', 'Loading content...', '<button class="btn btn-secondary" onclick="App.showVersions(\'' + id + '\')">Back</button>');

        try {
            const res = await this.authenticatedFetch(`/api/version?db=${this.state.currentDb}&col=${this.state.currentCol}&id=${id}&version=${version}`);
            if (res.ok) {
                const content = await res.json();
                const html = `
                    <div style="margin-bottom: 1rem;">
                        <strong>Version:</strong> ${new Date(parseInt(version)).toLocaleString()}
                    </div>
                    <pre style="background: var(--bg-secondary); padding: 1rem; border-radius: 4px; overflow: auto; max-height: 400px;"><code>${JSON.stringify(content, null, 2)}</code></pre>
                `;
                this.renderCustomModal('Version Content', html,
                    `<button class="btn btn-secondary" onclick="App.showVersions('${id}')">Back</button>`
                );
            } else {
                this.renderCustomModal('Version Content', '<div class="text-red-500">Failed to load content</div>',
                    `<button class="btn btn-secondary" onclick="App.showVersions('${id}')">Back</button>`
                );
            }
        } catch (e) {
            this.renderCustomModal('Version Content', '<div class="text-red-500">Error: ' + e.message + '</div>',
                `<button class="btn btn-secondary" onclick="App.showVersions('${id}')">Back</button>`
            );
        }
    },

    async restoreVersion(id, version) {
        if (!confirm(`Restore document to version from ${new Date(parseInt(version)).toLocaleString()}? Current state will be saved as a new version.`)) return;

        try {
            const res = await this.authenticatedFetch('/api/restore-version', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    db: this.state.currentDb,
                    col: this.state.currentCol,
                    id: id,
                    version: version
                })
            });

            if (res.ok) {
                this.showNotification('Document restored successfully', 'success');
                this.closeInputModal();
                this.loadDocuments(this.state.currentDb, this.state.currentCol);
            } else {
                const text = await res.text();
                this.showNotification('Restore failed: ' + text, 'error');
            }
        } catch (e) {
            this.showNotification('Error restoring version: ' + e.message, 'error');
        }
    },

    // --- Cluster Management ---
    showSection(sectionId) {
        console.log('Showing section:', sectionId);
        // Hiding all sections
        const sections = document.querySelectorAll('.section-content');
        sections.forEach(el => {
            el.classList.add('hidden');
            el.style.display = 'none'; // Force hide
        });

        document.getElementById('login-view').classList.remove('active');
        document.getElementById('dashboard-view').classList.add('active');

        // Show target
        const targetId = sectionId + '-section';
        const target = document.getElementById(targetId);
        if (target) {
            target.classList.remove('hidden');
            target.style.display = 'block'; // Force show

            // Manage Auto-Refresh for Cluster
            if (this.state.clusterInterval) {
                clearInterval(this.state.clusterInterval);
                this.state.clusterInterval = null;
            }

            if (sectionId === 'cluster') {
                this.refreshClusterStatus();
                this.state.clusterInterval = setInterval(() => this.refreshClusterStatus(), 5000);
            }
            if (sectionId === 'indexes') this.initIndexesView();
            if (sectionId === 'rules') this.initRulesView();
            if (sectionId === 'transactions') this.initTxView();
            if (sectionId === 'backups') this.loadBackups();
            if (sectionId === 'import-export') this.initImportExport();
            if (sectionId === 'invoices') this.initInvoicesView();
            if (sectionId === 'query') this.initQueryView();
            if (sectionId === 'users') this.initUsersView();
            if (sectionId === 'federated') this.initFederatedView();

            return;
        }

        // Fallback
        console.error('Target section not found:', targetId);
        const dbSection = document.getElementById('dashboard-section');
        if (dbSection) {
            dbSection.classList.remove('hidden');
            dbSection.style.display = 'block';
        }
    },

    // --- Query Console ---
    async initQueryView() {
        const dbSelect = document.getElementById('query-db-select');
        dbSelect.innerHTML = '<option value="">Select DB</option>';
        try {
            const res = await this.authenticatedFetch('/api/dbs');
            if (res.ok) {
                const dbs = await res.json();
                if (Array.isArray(dbs)) {
                    dbs.forEach(db => {
                        const opt = document.createElement('option');
                        opt.value = db.name;
                        opt.textContent = db.name;
                        dbSelect.appendChild(opt);
                    });
                    // Auto Select current if avail
                    if (this.state.currentDb) dbSelect.value = this.state.currentDb;
                }
            } else {
                console.error("Failed to load DBs for Query Console");
            }
        } catch (e) { console.error(e); }
    },

    async executeQuery() {
        const db = document.getElementById('query-db-select').value;
        const cmd = document.getElementById('query-input').value;
        const resultPanel = document.getElementById('query-results');

        if (!db) {
            this.showNotification('Please select a database', 'error');
            return;
        }
        if (!cmd.trim()) {
            this.showNotification('Please enter a command', 'error');
            return;
        }

        resultPanel.textContent = 'Executing...';

        try {
            const res = await this.authenticatedFetch(`/api/command?db=${db}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ command: cmd })
            });

            if (res.ok) {
                const json = await res.json();
                // Pretty print
                resultPanel.textContent = JSON.stringify(json, null, 2);

                // If it looks like a list of docs, maybe show table option? 
                // For now, keep it simple JSON as requested for console.
            } else {
                const text = await res.text();
                resultPanel.textContent = 'Error: ' + text;
                resultPanel.style.color = '#ff5555';
            }
        } catch (e) {
            console.error(e);
            resultPanel.textContent = 'Client Error: ' + e.message;
            resultPanel.style.color = '#ff5555';
        }
    },

    // --- User Management ---
    async initUsersView() {
        if (this.state.role !== 'admin') {
            this.showNotification("Access Denied: Admin only", "error");
            this.showSection('dashboard');
            return;
        }
        this.loadUsers();
    },

    async loadUsers() {
        try {
            const res = await this.authenticatedFetch('/api/users');
            if (!res.ok) throw new Error('Failed to load users');
            const users = await res.json();

            const tbody = document.getElementById('users-table-body');
            tbody.innerHTML = '';

            users.forEach(user => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td class="px-6 py-4">${user.username}</td>
                    <td class="px-6 py-4">${user.role}</td>
                    <td class="px-6 py-4 text-right">
                        <button onclick="app.deleteUser('${user._id || user.id}')" class="btn btn-sm btn-danger">Delete</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        } catch (e) {
            this.showNotification("Error loading users: " + e.message, 'error');
        }
    },

    async showAddUserModal() {
        let dbOptions = '';
        try {
            const dbsRes = await this.authenticatedFetch('/api/dbs');
            if (dbsRes.ok) {
                const dbs = await dbsRes.json();
                dbs.forEach(db => {
                    dbOptions += `
                        <label style="display:flex; align-items:center; margin-bottom:0.4rem;">
                            <input type="checkbox" name="allowed_dbs" value="${db.name}" style="margin-right:0.5rem;">
                            <span>${db.name}</span>
                        </label>
                    `;
                });
            }
        } catch (e) {
            console.error('Failed to load DBs', e);
        }

        const html = `
            <div class="form-group">
                <label>Username</label>
                <input type="text" id="new-user-name" class="form-control">
            </div>
            <div class="form-group">
                <label>Password</label>
                <input type="password" id="new-user-pass" class="form-control">
            </div>
            <div class="form-group">
                <label>Role</label>
                <select id="new-user-role" class="form-control">
                    <option value="reader">Reader</option>
                    <option value="writereader">Write/Read</option>
                    <option value="owner">Owner</option>
                    <option value="admin">Admin (Global Access)</option>
                </select>
            </div>
            <div class="form-group">
                <label>Allowed Databases (Optional for non-Admin)</label>
                <div id="db-checkboxes" style="max-height: 150px; overflow-y: auto; background: var(--bg-secondary); padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 4px;">
                    ${dbOptions || '<div class="text-muted">No databases found</div>'}
                </div>
            </div>
        `;

        this.showInputModal("Add New User", html, async () => {
            const username = document.getElementById('new-user-name').value;
            const password = document.getElementById('new-user-pass').value;
            const role = document.getElementById('new-user-role').value;

            const checkboxes = document.querySelectorAll('input[name="allowed_dbs"]:checked');
            const allowed_dbs = Array.from(checkboxes).map(c => c.value);

            if (!username || !password) {
                alert("Username and Password required");
                return;
            }

            await this.createUser(username, password, role, allowed_dbs);
        });
    },

    async createUser(username, password, role, allowed_dbs) {
        try {
            const body = { username, password, role };
            if (allowed_dbs && allowed_dbs.length > 0) {
                body.allowed_dbs = allowed_dbs;
            }

            const res = await this.authenticatedFetch('/api/users', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            if (res.ok) {
                this.showNotification("User created", "success");
                this.closeInputModal();
                this.loadUsers();
            } else {
                const text = await res.text();
                this.showNotification("Failed: " + text, "error");
            }
        } catch (e) {
            this.showNotification("Error: " + e.message, "error");
        }
    },

    async deleteUser(id) {
        if (!confirm("Delete user?")) return;
        try {
            await this.authenticatedFetch(`/api/users?id=${id}`, { method: 'DELETE' });
            this.showNotification("User deleted", "success");
            this.loadUsers();
        } catch (e) {
            this.showNotification("Failed to delete user", "error");
        }
    },

    // --- Invoice Management ---
    async initInvoicesView() {
        const select = document.getElementById('invoice-db-select');
        select.innerHTML = '<option value="">Select Database</option>';
        try {
            const res = await this.authenticatedFetch('/api/dbs');
            const dbs = await res.json();
            dbs.forEach(db => {
                const opt = document.createElement('option');
                opt.value = db.name;
                opt.textContent = db.name;
                select.appendChild(opt);
            });

            // Try to select 'almacenbasicdb' by default if exists or first one
            const defaultDb = dbs.find(d => d.name === 'almacenbasicdb') || dbs[0];
            if (defaultDb) {
                select.value = defaultDb.name;
                this.loadInvoices();
            }
        } catch (e) { console.error(e); }
    },

    state_invoicePage: 1,

    async loadInvoices() {
        const db = document.getElementById('invoice-db-select').value;
        if (!db) return;

        const tbody = document.getElementById('invoices-table-body');
        tbody.innerHTML = '<tr><td colspan="5">Loading...</td></tr>';

        const limit = 20;
        const offset = (this.state_invoicePage - 1) * limit;

        try { // Use query API to fetch invoices
            // Need 'facturas' collection
            const res = await this.authenticatedFetch(`/api/query?db=${db}&col=facturas&limit=${limit}&offset=${offset}`);
            if (!res.ok) throw new Error("Failed");
            const invoices = await res.json();

            tbody.innerHTML = '';
            if (invoices.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5">No invoices found.</td></tr>';
                return;
            }

            // Enhance display - maybe we can fetch client names? 
            // For benchmarking performance, maybe keep it simple first.

            invoices.forEach(inv => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${inv.id}</td>
                    <td>${inv.fecha}</td>
                    <td>${inv.cliente_id}</td>
                    <td>$${parseFloat(inv.total).toFixed(2)}</td>
                    <td>
                        <button class="btn btn-sm btn-info" onclick="app.viewInvoice('${inv.id}')">View</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });

            document.getElementById('invoice-page-num').textContent = `Page ${this.state_invoicePage}`;

        } catch (e) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-error">Error loading data. Collection "facturas" might not exist.</td></tr>';
        }
    },

    prevInvoicePage() {
        if (this.state_invoicePage > 1) {
            this.state_invoicePage--;
            this.loadInvoices();
        }
    },

    nextInvoicePage() {
        this.state_invoicePage++;
        this.loadInvoices();
    },

    async viewInvoice(id) {
        const db = document.getElementById('invoice-db-select').value;
        this.renderCustomModal('Invoice Details', 'Loading...', '<button class="btn btn-secondary" onclick="app.closeInputModal()">Close</button>');

        try {
            // Get Invoice Header
            const invRes = await this.authenticatedFetch(`/api/doc?db=${db}&col=facturas&id=${id}`); // Assuming query by ID or just filter
            // Actually getDocument by ID is usually via ID param if supported, strictly speaking Jettra ID logic is implicit for now?
            // Let's us query filter for safety if ID not direct key in storage yet
            // Assuming we use standard query:
            const qRes = await this.authenticatedFetch(`/api/query?db=${db}&col=facturas&limit=1`, {
                // We don't have WHERE yet in GET query param properly, usually filter client side for now or implement WHERE 
                // Wait, Jettra has no WHERE in GET param yet implemented simpler? 
                // Actually doc fetch via ID is supported: /api/doc?db=...&col=...&id=...
            });

            // Wait, I should use the correct endpoint I built.
            const docRes = await this.authenticatedFetch(`/api/doc?db=${db}&col=facturas&id=${id}`);
            const invoice = await docRes.json();

            // Get Details
            // Need to query details where factura_id = id.
            // Jettra currently has limited query. I'll fetch valid details? 
            // Implementing a client-side filter for now or we must implement `where` in server.
            // With 1M rows, fetching all is impossible.
            // I'll implement a specific query if needed, or rely on `DataSeeder` structure.
            // But wait, the previous `QueryExecutor` does NOT support WHERE params in URL.
            // It supports SQL-like or JQL via POST.

            // Let's use the POST /api/command for querying details!
            const cmdPayload = {
                command: `FIND IN facturasdetalles WHERE factura_id = "${id}"`
            };

            const detailsRes = await this.authenticatedFetch(`/api/command?db=${db}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(cmdPayload)
            });
            const details = await detailsRes.json();

            let detailsHtml = '<table class="data-table"><thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Subtotal</th></tr></thead><tbody>';
            details.forEach(d => {
                detailsHtml += `<tr><td>${d.producto_id}</td><td>${d.cantidad}</td><td>${d.precio_unitario}</td><td>${d.subtotal}</td></tr>`;
            });
            detailsHtml += '</tbody></table>';

            this.renderCustomModal(`Invoice #${invoice.id}`, `
                <div style="padding: 1rem;">
                    <div style="display:grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 2rem;">
                         <div><strong>Date:</strong> ${invoice.fecha}</div>
                         <div><strong>Client:</strong> ${invoice.cliente_id}</div>
                         <div><strong>Total:</strong> $${invoice.total}</div>
                    </div>
                    <h4>Items</h4>
                    ${detailsHtml}
                </div>
             `, '<button class="btn btn-secondary" onclick="app.closeInputModal()">Close</button>');

        } catch (e) {
            this.renderCustomModal('Error', 'Failed to load details: ' + e.message, '<button class="btn btn-secondary" onclick="app.closeInputModal()">Close</button>');
        }
    },

    showCreateInvoice() {
        const db = document.getElementById('invoice-db-select').value;
        if (!db) {
            this.showNotification("Select a database first", "error");
            return;
        }

        // We need a more complex form. 
        // 1. Client Search
        // 2. Product Search & Add to List
        // 3. Save

        this.renderCustomModal('New Invoice', `
            <div id="invoice-form-container" style="min-height: 400px;">
                <div class="form-group">
                    <label>Client (ID)</label>
                    <div style="display:flex; gap: 0.5rem;">
                        <input type="text" id="inv-client-id" class="form-control" placeholder="C0...">
                        <!-- In real app, search modal here -->
                    </div>
                </div>
                
                <div class="form-group" style="margin-top: 1rem; padding: 1rem; background: var(--bg-secondary); border-radius: 4px;">
                    <label>Add Product</label>
                    <div style="display:flex; gap: 0.5rem; margin-bottom: 0.5rem;">
                         <input type="text" id="inv-prod-id" class="form-control" placeholder="Product ID (P0...)" style="flex:2">
                         <input type="number" id="inv-prod-qty" class="form-control" placeholder="Qty" value="1" style="flex:1">
                         <input type="number" id="inv-prod-price" class="form-control" placeholder="Price" style="flex:1">
                         <button class="btn btn-primary" onclick="app.addInvoiceLine()">Add</button>
                    </div>
                </div>
                
                <div class="table-container" style="max-height: 200px; overflow-y: auto;">
                    <table class="data-table">
                        <thead><tr><th>Prod</th><th>Qty</th><th>Price</th><th>Total</th><th></th></tr></thead>
                        <tbody id="inv-lines-body"></tbody>
                    </table>
                </div>
                
                <div style="text-align: right; margin-top: 1rem; font-size: 1.2em;">
                    <strong>Total: $<span id="inv-total-display">0.00</span></strong>
                </div>
            </div>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-success" onclick="app.saveInvoice()">Save Invoice</button>
        `);

        // Reset temp state for lines
        this.tempInvoiceLines = [];
    },

    tempInvoiceLines: [],

    addInvoiceLine() {
        const pid = document.getElementById('inv-prod-id').value;
        const qty = parseInt(document.getElementById('inv-prod-qty').value);
        const price = parseFloat(document.getElementById('inv-prod-price').value);

        if (!pid || isNaN(qty) || isNaN(price)) return;

        this.tempInvoiceLines.push({ pid, qty, price, subtotal: qty * price });
        this.renderInvoiceLines();

        // Clear inputs
        document.getElementById('inv-prod-id').value = '';
        document.getElementById('inv-prod-qty').value = '1';
        document.getElementById('inv-prod-price').value = '';
    },

    renderInvoiceLines() {
        const tbody = document.getElementById('inv-lines-body');
        tbody.innerHTML = '';
        let total = 0;

        this.tempInvoiceLines.forEach((line, idx) => {
            total += line.subtotal;
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${line.pid}</td>
                <td>${line.qty}</td>
                <td>${line.price}</td>
                <td>${line.subtotal}</td>
                <td><button class="btn btn-sm btn-danger" onclick="app.removeInvoiceLine(${idx})">x</button></td>
            `;
            tbody.appendChild(tr);
        });

        document.getElementById('inv-total-display').textContent = total.toFixed(2);
    },

    removeInvoiceLine(idx) {
        this.tempInvoiceLines.splice(idx, 1);
        this.renderInvoiceLines();
    },

    async saveInvoice() {
        const db = document.getElementById('invoice-db-select').value;
        const clientId = document.getElementById('inv-client-id').value;

        if (!clientId || this.tempInvoiceLines.length === 0) {
            alert("Client and at least one item required");
            return;
        }

        const invoiceId = 'F' + Date.now(); // Simple ID gen

        const invoice = {
            id: invoiceId,
            fecha: new Date().toISOString(),
            cliente_id: clientId,
            total: this.tempInvoiceLines.reduce((acc, l) => acc + l.subtotal, 0)
        };

        try {
            // Save Header
            await this.authenticatedFetch(`/api/doc?db=${db}&col=facturas`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(invoice)
            });

            // Save Lines
            // Ideally transactional but we do best effort manually for this prototype
            for (const line of this.tempInvoiceLines) {
                const detail = {
                    factura_id: invoiceId,
                    producto_id: line.pid,
                    cantidad: line.qty,
                    precio_unitario: line.price,
                    subtotal: line.subtotal
                };
                await this.authenticatedFetch(`/api/doc?db=${db}&col=facturasdetalles`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(detail)
                });
            }

            this.showNotification("Invoice Saved", "success");
            this.closeInputModal();
            this.loadInvoices();

        } catch (e) {
            this.showNotification("Error saving invoice: " + e.message, "error");
        }
    },

    async refreshClusterStatus() {
        const content = document.getElementById('raft-status-content');
        content.innerHTML = 'Loading...';
        try {
            // Use authenticated endpoint
            const res = await this.authenticatedFetch('/api/cluster');
            if (!res.ok) throw new Error('Failed to fetch status');

            const status = await res.json();
            console.log("Cluster status received:", status);

            // Status is { enabled: true, nodeId: "...", state: "...", leaderId: "...", term: ..., nodes: [...] }

            if (!status.enabled) {
                content.innerHTML = '<div class="alert alert-warning">Raft Cluster is not enabled on this node.</div>';
                return;
            }

            content.innerHTML = `
                <div class="space-y-2">
                    <div class="flex justify-between border-b dark:border-gray-700 pb-1" style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 0.5rem;">
                        <span>Node ID:</span> <span class="font-mono">${status.nodeId}</span>
                    </div>
                    <div class="flex justify-between border-b dark:border-gray-700 pb-1" style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 0.5rem;">
                        <span>State:</span> <span class="status-badge ${status.state === 'LEADER' ? 'success' : 'warning'}">${status.state}</span>
                    </div>
                    <div class="flex justify-between border-b dark:border-gray-700 pb-1" style="display: flex; justify-content: space-between; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 0.5rem;">
                        <span>Term:</span> <span>${status.term}</span>
                    </div>
                    <div class="flex justify-between border-b dark:border-gray-700 pb-1" style="display: flex; justify-content: space-between; padding-bottom: 0.5rem;">
                        <span>Leader:</span> <span class="font-mono">${status.leaderId || 'None'}</span>
                    </div>
                </div>
            `;

            // Normalize nodes loop
            const nodeList = (status.nodes || []).sort((a, b) => {
                const idA = a.id || a._id || '';
                const idB = b.id || b._id || '';
                return String(idA).localeCompare(String(idB));
            });

            // Render Cluster Map
            const mapContainer = document.getElementById('cluster-visual-map');
            if (mapContainer) {
                mapContainer.innerHTML = '';
                nodeList.forEach((n, index) => {
                    const nid = n.id || n._id || ('node-' + (n.url ? n.url.hashCode() : Math.random()));
                    console.log("Visualizing node:", nid, n);
                    const isLeader = status.leaderId && (nid === status.leaderId);
                    const isSelf = (nid === status.nodeId);
                    // Improve node naming: try to get number from ID, fallback to address
                    let derivedName = `Node ?`;
                    const idMatch = nid.match(/node-(\d+)/);
                    if (idMatch && idMatch[1].length < 5) {
                        derivedName = `Node ${idMatch[1]}`;
                    } else if (n.url) {
                        const portMatch = n.url.match(/:(\d+)/);
                        derivedName = portMatch ? `Port ${portMatch[1]}` : n.url.replace('http://', '');
                    }

                    let roleClass = 'node-follower';
                    if (isLeader) roleClass = 'node-leader';
                    else if (n.role === 'LEADER') roleClass = 'node-leader'; // Fallback for UI if leaderId not yet broadcast but DB says so

                    if (n.status === 'INACTIVE') roleClass = 'node-inactive';
                    if (n.status === 'PAUSED') roleClass = 'node-paused';
                    if (n.state === 'CANDIDATE') roleClass = 'node-candidate';

                    const selfClass = isSelf ? 'node-self' : '';

                    const displayName = n.description || (n.url ? n.url.replace('http://', '') : nid.substring(0, 8));
                    const metricsHtml = n.metrics ? `
                        <div style="font-size: 0.45rem; color: #00ff9d; line-height: 1.1; margin-top: 4px; padding: 2px; background: rgba(0,0,0,0.3); border-radius: 4px; width: 90%;">
                            CPU: ${n.metrics.cpuUsage}%<br/>
                            RAM-F: ${n.metrics.ramFreeStr || 'N/A'}<br/>
                            DSK-F: ${n.metrics.diskFreeStr || 'N/A'}
                        </div>
                    ` : '<div style="font-size: 0.45rem; opacity: 0.5; margin-top: 4px;">(Pending Metrics)</div>';

                    const el = document.createElement('div');
                    el.className = `node-circle ${roleClass} ${selfClass}`;
                    el.style.display = 'flex';
                    el.style.flexDirection = 'column';
                    el.style.alignItems = 'center';
                    el.style.justifyContent = 'center';
                    el.style.textAlign = 'center';
                    el.style.padding = '5px';

                    el.innerHTML = `
                        <div style="font-size: 0.7rem; font-weight: 900; color: #ffffff; text-shadow: 0 1px 2px rgba(0,0,0,0.5);">${derivedName}</div>
                        <div style="font-size: 0.42rem; opacity: 0.8; font-family: monospace; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 75px;">${displayName}</div>
                        <div style="font-size: 0.5rem; font-weight: bold; margin-top: 2px; color: ${isLeader ? '#ffca28' : '#e0e0e0'}">
                            ${isLeader ? '👑 LEADER' : 'FOLLOWER'}
                        </div>
                        ${metricsHtml}
                    `;
                    el.title = `ID: ${nid}\nURL: ${n.url}\nRole: ${n.role}\nStatus: ${n.status}\nState: ${n.state || 'N/A'}`;
                    mapContainer.appendChild(el);
                });
            }

            // Render Nodes List
            const renderNodes = (nodes) => {
                console.log("RenderNodes called with:", nodes);
                const tbody = document.getElementById('peers-table-body');
                if (!tbody) return;

                if (!nodes || nodes.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5" class="px-6 py-4 text-center">No nodes found</td></tr>';
                    return;
                }

                let html = '';
                nodes.sort((a, b) => {
                    const idA = a.id || a._id || '';
                    const idB = b.id || b._id || '';
                    return String(idA).localeCompare(String(idB));
                });

                nodes.forEach(node => {
                    const nid = node.id || node._id || 'unknown';
                    console.log("Processing node for table:", node, "ID:", nid);
                    const isLeader = status.leaderId ? (nid === status.leaderId) : (node.role === 'LEADER');
                    const isSelf = nid === status.nodeId;

                    let roleLabel = node.role || 'FOLLOWER';
                    if (isLeader) roleLabel = "👑 LEADER";

                    let statusBadge = 'secondary';
                    if (node.status === 'ACTIVE') statusBadge = 'success';
                    if (node.status === 'INACTIVE' || node.status === 'PAUSED') statusBadge = 'danger';

                    html += `
                        <tr class="bg-white border-b dark:bg-gray-800 dark:border-gray-700">
                            <td class="px-6 py-4 font-medium text-gray-900 whitespace-nowrap dark:text-white">
                                <div class="flex flex-col">
                                    <span class="font-mono text-sm">${nid}</span>
                                    ${isSelf ? '<span class="text-xs text-muted">(This Node)</span>' : ''}
                                </div>
                            </td>
                            <td class="px-3 py-2">
                                <span class="px-2 py-0.5 rounded text-xs ${node.status === 'ACTIVE' ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}">
                                    ${node.status}
                                </span>
                            </td>
                            <td class="px-3 py-2 text-xs">
                                ${node.metrics ? `
                                    <div class="text-xs space-y-1">
                                        <div class="flex justify-between"><span>CPU:</span> <b>${node.metrics.cpuUsage}%</b></div>
                                        <div class="flex justify-between"><span>RAM:</span> <b>${node.metrics.ramUsedStr} / ${node.metrics.ramTotalStr}</b></div>
                                        <div class="flex justify-between"><span>Disk:</span> <b>${node.metrics.diskUsedStr} / ${node.metrics.diskTotalStr}</b></div>
                                    </div>
                                ` : '<span class="text-muted text-xs">No metrics</span>'}
                            </td>
                            <td class="px-6 py-4">
                                <div class="flex gap-2">
                                    <button onclick="app.viewNodeMetrics('${nid}')" class="px-3 py-1 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700 transition-colors">
                                        Metrics
                                    </button>
                                    ${!isSelf ? `
                                        <button onclick="app.pauseNode('${nid}')" class="px-3 py-1 text-xs font-medium text-white bg-yellow-600 rounded hover:bg-yellow-700 transition-colors">Pause</button>
                                        <button onclick="app.stopNode('${nid}')" class="px-3 py-1 text-xs font-medium text-white bg-red-600 rounded hover:bg-red-700 transition-colors">Stop</button>
                                    ` : ''}
                                </div>
                            </td>
                        </tr>
                     `;
                });
                tbody.innerHTML = html;
            };

            renderNodes(nodeList);

        } catch (e) {
            content.innerHTML = '<span class="text-red-500">Error loading status: ' + e.message + '</span>';
            console.error(e);
        }
    },

    async addPeer() {
        const url = document.getElementById('peer-url').value;
        const desc = document.getElementById('peer-desc').value;

        if (!url) {
            alert("Please enter the Peer URL");
            return;
        }

        // Auto-fix URL if missing http
        let cleanUrl = url.trim();
        if (!cleanUrl.startsWith('http')) {
            cleanUrl = 'http://' + cleanUrl;
        }

        try {
            const res = await this.authenticatedFetch('/api/cluster/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: cleanUrl, description: desc })
            });

            if (res.ok) {
                document.getElementById('peer-url').value = '';
                document.getElementById('peer-desc').value = '';
                this.refreshClusterStatus();
                this.showNotification('Node added to cluster', 'success');
            } else {
                const text = await res.text();
                this.showNotification('Failed to add peer: ' + text, 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error adding peer: ' + e.message, 'error');
        }
    },

    async removePeer(id, url) {
        if (!confirm('Remove Node ' + id + ' (' + url + ') from cluster configuration? This does not stop the node process, only removes it from Raft config.')) return;
        try {
            const res = await this.authenticatedFetch('/api/cluster/deregister', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: url })
            });

            if (res.ok) {
                this.refreshClusterStatus();
                this.showNotification('Node removed from cluster', 'success');
            } else {
                const text = await res.text();
                this.showNotification('Failed to remove peer: ' + text, 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error removing peer', 'error');
        }
    },

    async stopNode(url) {
        if (!confirm('Stop Node process at ' + url + '? This will shut down the remote server.')) return;
        try {
            const res = await this.authenticatedFetch('/api/cluster/stop', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ node: url })
            });

            if (res.ok) {
                this.showNotification('Stop command sent to node', 'success');
                setTimeout(() => this.refreshClusterStatus(), 2000);
            } else {
                const text = await res.text();
                this.showNotification('Failed to stop node: ' + text, 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error stopping node', 'error');
        }
    },

    async pauseNode(url) {
        try {
            const res = await this.authenticatedFetch('/api/cluster/pause', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ node: url })
            });

            if (res.ok) {
                this.showNotification('Node Paused (Data flow stopped)', 'success');
                this.refreshClusterStatus();
            } else {
                const text = await res.text();
                this.showNotification('Failed to pause node: ' + text, 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error pausing node', 'error');
        }
    },

    async resumeNode(url) {
        try {
            const res = await this.authenticatedFetch('/api/cluster/resume', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ node: url })
            });

            if (res.ok) {
                this.showNotification('Node Resumed', 'success');
                this.refreshClusterStatus();
            } else {
                const text = await res.text();
                this.showNotification('Failed to resume node: ' + text, 'error');
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Error resuming node', 'error');
        }
    },

    async forceLeader() {
        if (!confirm('DANGEROUS: Forcing this node to be leader can cause split-brain if another leader exists. Continue?')) return;
        try {
            // This endpoint might still be direct Raft, or we need to expose it in WebServices if we want it authenticated.
            // Currently WebServices does NOT expose /api/cluster/force-leader
            // Let's assume for now we keep the direct call if it exists or wrap it.
            // Ideally we wrap it. But checking WebServices again... it does not have forceLeader.
            // Engine's RaftService registers /raft/rpc... 
            // We'll leave as is but warn it might fail if auth required on /raft path or if not exposed.
            // Actually, let's try calling the raft service directly as before, assuming it's open.
            const res = await fetch('/raft/force-leader', { method: 'POST' });
            if (res.ok) {
                this.refreshClusterStatus();
                this.showNotification('Leader forced', 'success');
            } else {
                alert('Failed to force leader');
            }
        } catch (e) {
            console.error(e);
        }
    },

    // --- Index Management ---
    async loadIndexCollections() {
        const dbSelect = document.getElementById('idx-db-select');
        const db = dbSelect.value;
        const colSelect = document.getElementById('idx-col-select');
        colSelect.innerHTML = '';

        if (!db) return;

        try {
            const res = await this.authenticatedFetch(`/api/dbs/${db}/cols`);
            const cols = await res.json();
            cols.forEach(col => {
                const opt = document.createElement('option');
                opt.value = col;
                opt.textContent = col;
                colSelect.appendChild(opt);
            });
            this.loadIndexes();
        } catch (e) { console.error(e); }
    },

    async loadIndexes() {
        const db = document.getElementById('idx-db-select').value;
        const col = document.getElementById('idx-col-select').value;
        const list = document.getElementById('indexes-list');
        list.innerHTML = 'Loading...';

        if (!db || !col) {
            list.innerHTML = 'Select a database and collection.';
            return;
        }

        try {
            const res = await this.authenticatedFetch(`/api/index?db=${db}&col=${col}`);
            const indexes = await res.json();
            list.innerHTML = '';

            if (indexes.length === 0) {
                list.innerHTML = '<li>No indexes found.</li>';
                return;
            }

            indexes.forEach(idx => {
                const li = document.createElement('li');
                li.style.padding = '0.5rem';
                li.style.borderBottom = '1px solid var(--border-color)';
                li.innerHTML = `
                    <strong>${idx.field}</strong> 
                    <span class="badge ${idx.unique ? 'badge-primary' : 'badge-secondary'}">${idx.unique ? 'Unique' : 'Standard'}</span>
                    ${idx.sequential ? '<span class="badge badge-secondary">Sequential</span>' : ''}
                    <button class="btn btn-xs btn-danger" style="margin-left: 1rem;" 
                        onclick="app.deleteIndex('${db}', '${col}', '${idx.field}')">Delete</button>
                `;
                list.appendChild(li);
            });
        } catch (e) {
            list.innerHTML = 'Error loading indexes';
            console.error(e);
        }
    },

    createIndex() {
        const db = document.getElementById('idx-db-select').value;
        const col = document.getElementById('idx-col-select').value;
        const field = document.getElementById('idx-field').value;
        const type = document.getElementById('idx-type').value;
        const sequential = document.getElementById('idx-sequential').checked;

        if (!db || !col || !field) {
            this.showNotification("Please fill all fields", 'warning');
            return;
        }

        this.renderCustomModal('Confirm Index Creation', `
            <div class="alert alert-info" style="background: #e1f5fe; color: #01579b; padding: 15px; border-radius: 5px; margin-bottom: 20px;">
                 <strong>ℹ️ Index Details</strong>
                 <p style="margin: 10px 0 0;">You are about to create a <strong>${type}</strong> index on field <strong>'${field}'</strong> for collection <strong>${col}</strong>.</p>
                 ${sequential ? '<p style="margin-top:5px;"><em>Sequential optimization enabled.</em></p>' : ''}
            </div>
            <p>Do you want to proceed?</p>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn btn-primary" id="modal-confirm-create-index-btn">Create Index</button>
        `);

        document.getElementById('modal-confirm-create-index-btn').addEventListener('click', async () => {
            this.closeInputModal();
            try {
                this.showNotification('Creating index...', 'info');
                const res = await this.authenticatedFetch('/api/index', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        database: db,
                        collection: col,
                        field: field,
                        unique: type === 'unique',
                        sequential: sequential
                    })
                });

                if (res.ok) {
                    this.showNotification('Index created successfully', 'success');
                    document.getElementById('idx-field').value = '';
                    document.getElementById('idx-sequential').checked = false;
                    this.loadIndexes();
                } else {
                    const text = await res.text();
                    this.showNotification('Failed to create index: ' + text, 'error');
                }
            } catch (e) {
                console.error(e);
                this.showNotification('Error creating index: ' + e.message, 'error');
            }
        });
    },

    deleteIndex(db, col, field) {
        this.renderCustomModal('Delete Index', `
            <div class="alert alert-danger" style="background: #f8d7da; color: #721c24; padding: 15px; border-radius: 5px; margin-bottom: 20px;">
                 <strong>⚠️ Confirm Deletion</strong>
                 <p style="margin: 10px 0 0;">Are you sure you want to delete the index on field <strong>'${field}'</strong>?</p>
            </div>
        `, `
            <button class="btn btn-secondary" onclick="app.closeInputModal()">Cancel</button>
            <button class="btn danger" id="modal-confirm-delete-index-btn">Delete Index</button>
        `);

        // Bind logic to the dynamically created button
        document.getElementById('modal-confirm-delete-index-btn').addEventListener('click', async () => {
            try {
                this.showNotification('Deleting index...', 'info');
                const res = await this.authenticatedFetch(`/api/index?db=${db}&col=${col}&field=${field}`, { method: 'DELETE' });
                if (res.ok) {
                    this.showNotification('Index deleted successfully', 'success');
                    this.closeInputModal();
                    this.loadIndexes();
                } else {
                    const text = await res.text();
                    this.showNotification('Failed to delete index: ' + text, 'error');
                }
            } catch (e) {
                console.error(e);
                this.showNotification('Error deleting index: ' + e.message, 'error');
            }
        });
    },

    // Initialize Indexes View
    async initIndexesView() {
        const dbSelect = document.getElementById('idx-db-select');
        dbSelect.innerHTML = '<option value="">Select DB</option>';
        try {
            const res = await this.authenticatedFetch('/api/dbs');
            const dbs = await res.json();
            dbs.forEach(db => {
                const opt = document.createElement('option');
                opt.value = db.name;
                opt.textContent = db.name;
                dbSelect.appendChild(opt);
            });
        } catch (e) { console.error(e); }
    },

    // --- Rules Management ---
    initRulesView() {
        this.loadRuleDatabases();
    },

    loadRuleDatabases() {
        this.authenticatedFetch('/api/dbs')
            .then(res => res.json())
            .then(dbs => {
                const sel = document.getElementById('rule-db-select');
                sel.innerHTML = '<option value="">Select Database</option>';
                dbs.forEach(db => {
                    const opt = document.createElement('option');
                    opt.value = db.name;
                    opt.textContent = db.name;
                    sel.appendChild(opt);
                });
            });
    },

    loadRuleCollections() {
        const db = document.getElementById('rule-db-select').value;
        const sel = document.getElementById('rule-col-select');
        sel.innerHTML = '<option value="">Select Collection</option>';
        if (!db) return;

        this.authenticatedFetch(`/api/dbs/${db}/cols`)
            .then(res => res.json())
            .then(cols => {
                cols.forEach(col => {
                    if (col !== '_rules' && col !== '_info') {
                        const opt = document.createElement('option');
                        opt.value = col;
                        opt.textContent = col;
                        sel.appendChild(opt);
                    }
                });
            });
    },

    toggleRuleInputs() {
        const type = document.getElementById('rule-type').value;
        const valGroup = document.getElementById('rule-val-group');
        const refColGroup = document.getElementById('rule-ref-col-group');
        const refFieldGroup = document.getElementById('rule-ref-field-group');

        if (type === 'referenced') {
            valGroup.classList.add('hidden');
            refColGroup.classList.remove('hidden');
            refFieldGroup.classList.remove('hidden');
        } else {
            valGroup.classList.remove('hidden');
            refColGroup.classList.add('hidden');
            refFieldGroup.classList.add('hidden');
        }
    },

    loadRules() {
        const db = document.getElementById('rule-db-select').value;
        const col = document.getElementById('rule-col-select').value;
        const tbody = document.getElementById('rules-table-body');
        tbody.innerHTML = '';
        if (!db || !col) return;

        this.authenticatedFetch(`/api/query?db=${db}&col=_rules&limit=100`)
            .then(res => res.json())
            .then(docs => {
                let rules = [];
                docs.forEach(doc => {
                    if (doc[col]) {
                        const colRules = doc[col];
                        if (Array.isArray(colRules)) {
                            colRules.forEach(ruleItem => {
                                const fieldName = Object.keys(ruleItem)[0];
                                const ruleDef = ruleItem[fieldName];
                                rules.push({ field: fieldName, ...ruleDef, _docId: doc._id || doc.id });
                            });
                        }
                    }
                });

                let html = '';
                rules.forEach((r, idx) => {
                    let valDisplay = r.value || '';
                    if (r.type === 'referenced') {
                        valDisplay = `Ref: ${r.collectionreferenced} (${r.externalfield})`;
                    }

                    html += `<tr>
                        <td>${r.field}</td>
                        <td>${r.type}</td>
                        <td>${valDisplay}</td>
                        <td>
                             <button class="btn btn-sm btn-danger" onclick="App.deleteRule('${r._docId}', '${col}', '${r.field}')">Delete</button>
                        </td>
                    </tr>`;
                });
                if (rules.length === 0) {
                    html = '<tr><td colspan="4" class="text-center">No rules defined</td></tr>';
                }
                tbody.innerHTML = html;
            });
    },

    addRule() {
        const db = document.getElementById('rule-db-select').value;
        const col = document.getElementById('rule-col-select').value;
        const field = document.getElementById('rule-field').value;
        const type = document.getElementById('rule-type').value;

        if (!db || !col || !field) {
            alert("Please select DB, Collection and enter Field Name");
            return;
        }

        let ruleDef = { type: type };
        if (type === 'referenced') {
            const refCol = document.getElementById('rule-ref-col').value;
            const refField = document.getElementById('rule-ref-field').value;
            if (!refCol || !refField) {
                alert("Please enter Referenced Collection and External Field");
                return;
            }
            ruleDef.collectionreferenced = refCol;
            ruleDef.externalfield = refField;
        } else {
            const val = document.getElementById('rule-value').value;
            if (['min_value', 'max_value'].includes(type)) {
                if (!val) { alert("Please enter a value"); return; }
                ruleDef.value = Number(val);
            } else {
                ruleDef.value = val ? val : "notnull"; // default or logic
            }
        }

        this.authenticatedFetch(`/api/query?db=${db}&col=_rules&limit=1`)
            .then(res => res.json())
            .then(docs => {
                let doc = {};
                if (docs.length > 0) {
                    doc = docs[0];
                }

                if (!doc[col]) {
                    doc[col] = [];
                }

                let newRuleItem = {};
                newRuleItem[field] = ruleDef;
                doc[col].push(newRuleItem);

                let method = 'POST';
                let url = `/api/doc?db=${db}&col=_rules`;
                if (doc._id || doc.id) {
                    method = 'PUT';
                    url += `&id=${doc._id || doc.id}`;
                }

                this.authenticatedFetch(url, {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc)
                }).then(async res => {
                    if (res.ok) {
                        this.loadRules();
                        document.getElementById('rule-field').value = '';
                        document.getElementById('rule-value').value = '';
                    } else {
                        const text = await res.text();
                        alert('Failed to save rule: ' + text);
                    }
                });
            });
    },

    deleteRule(docId, col, field) {
        if (!confirm('Delete rule?')) return;
        const db = document.getElementById('rule-db-select').value;

        this.authenticatedFetch(`/api/doc?db=${db}&col=_rules&id=${docId}`)
            .then(res => res.json())
            .then(doc => {
                if (doc && doc[col]) {
                    doc[col] = doc[col].filter(item => !item[field]);
                    this.authenticatedFetch(`/api/doc?db=${db}&col=_rules&id=${docId}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(doc)
                    }).then(() => this.loadRules());
                }
            });
    },

    // --- Transaction Management ---
    initTxView() {
        this.loadTxDatabases();
    },

    loadTxDatabases() {
        this.authenticatedFetch('/api/dbs')
            .then(res => res.json())
            .then(dbs => {
                const sel = document.getElementById('tx-db-select');
                sel.innerHTML = '<option value="">Select Database</option>';
                dbs.forEach(db => {
                    const opt = document.createElement('option');
                    opt.value = db.name;
                    opt.textContent = db.name;
                    sel.appendChild(opt);
                });

                // On change, load cols
                sel.onchange = () => {
                    const db = sel.value;
                    const colSel = document.getElementById('tx-col-select');
                    colSel.innerHTML = '<option value="">Select Collection</option>';
                    if (!db) return;
                    this.authenticatedFetch(`/api/dbs/${db}/cols`)
                        .then(res => res.json())
                        .then(cols => {
                            cols.forEach(col => {
                                const opt = document.createElement('option');
                                opt.value = col;
                                opt.textContent = col;
                                colSel.appendChild(opt);
                            });
                        });
                };
            });
    },

    async beginTx() {
        if (this.state.currentTxID) {
            this.showNotification("Transaction already active", 'warning');
            return;
        }
        try {
            const res = await this.authenticatedFetch('/api/tx/begin', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                this.state.currentTxID = data.txID;
                this.updateTxStatus();
                this.logTx("Transaction STARTED: " + data.txID);
                this.showNotification("Transaction Started", 'success');
            } else {
                this.logTx("Error starting Tx: " + res.status);
            }
        } catch (e) { console.error(e); this.logTx("Error: " + e.message); }
    },

    async commitTx() {
        if (!this.state.currentTxID) return;
        try {
            const res = await this.authenticatedFetch(`/api/tx/commit?txID=${this.state.currentTxID}`, { method: 'POST' });
            if (res.ok) {
                this.logTx("Transaction COMMITTED");
                this.state.currentTxID = null;
                this.updateTxStatus();
                this.showNotification("Transaction Committed", 'success');
            } else {
                this.logTx("Commit Failed: " + await res.text());
            }
        } catch (e) { console.error(e); this.logTx("Error: " + e.message); }
    },

    async rollbackTx() {
        if (!this.state.currentTxID) return;
        try {
            const res = await this.authenticatedFetch(`/api/tx/rollback?txID=${this.state.currentTxID}`, { method: 'POST' });
            if (res.ok) {
                this.logTx("Transaction ROLLED BACK");
                this.state.currentTxID = null;
                this.updateTxStatus();
                this.showNotification("Transaction Rolled Back", 'info');
            } else {
                this.logTx("Rollback Failed: " + await res.text());
            }
        } catch (e) { console.error(e); this.logTx("Error: " + e.message); }
    },

    async addToTx(type) {
        if (!this.state.currentTxID) {
            this.showNotification("No Active Transaction!", 'error');
            return;
        }
        const db = document.getElementById('tx-db-select').value;
        const col = document.getElementById('tx-col-select').value;
        if (!db || !col) { alert("Select DB and Collection"); return; }

        try {
            if (type === 'save') {
                const jsonStr = document.getElementById('tx-doc-json').value;
                let doc;
                try { doc = JSON.parse(jsonStr); } catch (e) { alert("Invalid JSON"); return; }

                let url = `/api/doc?db=${db}&col=${col}&tx=${this.state.currentTxID}`;

                const res = await this.authenticatedFetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc)
                });

                if (res.ok) {
                    const savedId = await res.text();
                    doc._id = savedId;
                    this.logTx(`[SAVE] Added to Tx. ID: ${savedId} Content: ${JSON.stringify(doc)}`);
                } else {
                    this.logTx(`[SAVE] Failed: ${await res.text()}`);
                }
            } else if (type === 'delete') {
                const id = document.getElementById('tx-del-id').value;
                if (!id) { alert("Enter ID"); return; }

                const url = `/api/doc?db=${db}&col=${col}&id=${id}&tx=${this.state.currentTxID}`;
                const res = await this.authenticatedFetch(url, { method: 'DELETE' });
                if (res.ok) {
                    this.logTx(`[DELETE] Added to Tx. ID: ${id}`);
                } else {
                    this.logTx(`[DELETE] Failed: ${await res.text()}`);
                }
            }
        } catch (e) {
            this.logTx(`Error in op ${type}: ${e.message}`);
        }
    },

    updateTxStatus() {
        const el = document.getElementById('tx-status');
        if (this.state.currentTxID) {
            el.textContent = "Active: " + this.state.currentTxID.substring(0, 8) + "...";
            el.style.color = "#0f0";
        } else {
            el.textContent = "No Active Tx";
            el.style.color = "var(--text-muted)";
        }
    },

    logTx(msg) {
        const log = document.getElementById('tx-log');
        const line = document.createElement('div');
        line.textContent = `[${new Date().toLocaleTimeString()}] ${msg}`;
        log.appendChild(line);
        log.scrollTop = log.scrollHeight;
    },

    async initImportExport() {
        try {
            const res = await this.authenticatedFetch('/api/dbs');
            if (res.ok) {
                const dbs = await res.json();
                const populate = (id) => {
                    const el = document.getElementById(id);
                    if (!el) return;
                    el.innerHTML = dbs.map(d => `<option value="${d.name}">${d.name}</option>`).join('');
                };
                populate('export-db-select');
                populate('import-db-select');

                // Trigger initial load of collections
                if (dbs.length > 0) {
                    this.loadExportCollections();
                    this.loadImportCollections();
                }
            }
        } catch (e) {
            console.error(e);
            this.showNotification('Failed to load databases', 'error');
        }
    },

    async loadExportCollections() {
        const db = document.getElementById('export-db-select').value;
        if (!db) return;
        try {
            const res = await this.authenticatedFetch(`/api/dbs/${db}/cols`);
            const cols = await res.json();
            const select = document.getElementById('export-col-select');
            select.innerHTML = cols.map(c => `<option value="${c}">${c}</option>`).join('');
        } catch (e) { console.error(e); }
    },

    async loadImportCollections() {
        const db = document.getElementById('import-db-select').value;
        if (!db) return;
        try {
            const res = await this.authenticatedFetch(`/api/dbs/${db}/cols`);
            const cols = await res.json();
            const select = document.getElementById('import-col-select');
            select.innerHTML = cols.map(c => `<option value="${c}">${c}</option>`).join('');
        } catch (e) { console.error(e); }
    },

    performExport() {
        const db = document.getElementById('export-db-select').value;
        const col = document.getElementById('export-col-select').value;
        const format = document.getElementById('export-format').value;

        if (!db || !col) {
            this.showNotification("Select database and collection", "error");
            return;
        }

        this.showNotification("Exporting...", "info");

        this.authenticatedFetch(`/api/export?db=${db}&col=${col}&format=${format}`)
            .then(res => {
                if (res.ok) return res.blob();
                throw new Error("Export failed");
            })
            .then(blob => {
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.style.display = 'none';
                a.href = url;
                a.download = `${col}.${format}`;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                this.showNotification("Export complete", "success");
            })
            .catch(e => {
                this.showNotification("Export failed: " + e.message, "error");
            });
    },

    async performImport() {
        const db = document.getElementById('import-db-select').value;
        const colSelect = document.getElementById('import-col-select').value;
        const colNew = document.getElementById('import-new-col').value;
        const format = document.getElementById('import-format').value;
        const fileInput = document.getElementById('import-file');

        const col = colNew ? colNew : colSelect;

        if (!db || !col) {
            this.showNotification("Select or enter a target collection", "error");
            return;
        }

        if (fileInput.files.length === 0) {
            this.showNotification("Select a file", "error");
            return;
        }

        const file = fileInput.files[0];
        this.showNotification("Importing...", "info");
        try {
            const res = await fetch(`${this.baseUrl}/api/import?db=${db}&col=${col}&format=${format}`, {
                method: 'POST',
                headers: {
                    'Authorization': localStorage.getItem('jettra_token')
                },
                body: file
            });

            if (res.ok) {
                const json = await res.json();
                this.showNotification(`Imported ${json.count} documents`, "success");
                if (colNew) {
                    this.loadImportCollections();
                    document.getElementById('import-new-col').value = '';
                }
            } else {
                const text = await res.text();
                this.showNotification("Import failed: " + text, "error");
            }
        } catch (e) {
            this.showNotification("Import error: " + e.message, "error");
        }
    }
    ,

    // --- Cluster ---
    async refreshClusterStatus() {
        const content = document.getElementById('raft-status-content');
        const tbody = document.getElementById('peers-table-body');
        if (!content) return;

        content.innerHTML = 'Checking...';

        try {
            const res = await this.authenticatedFetch('/api/cluster');
            if (res.ok) {
                const status = await res.json();
                if (!status.enabled) {
                    content.innerHTML = '<div class="alert alert-warning" style="background:#fff3cd; color:#856404; padding:10px;">Cluster mode is disabled. Set "distributed": true in config.json</div>';
                    if (tbody) tbody.innerHTML = '';
                    return;
                }

                const isLeader = status.state === 'LEADER';

                content.innerHTML = `
                    <div style="display:grid; grid-template-columns: auto 1fr; gap: 10px; font-size: 1.1em;">
                        <strong>Node ID:</strong> <span class="font-mono">${status.nodeId}</span>
                        <strong>State:</strong> <span class="badge ${isLeader ? 'bg-success' : 'bg-primary'}" style="padding:2px 8px; border-radius:4px; background:${isLeader ? '#28a745' : '#007bff'}; color:white;">${status.state}</span>
                        <strong>Leader:</strong> <span class="font-mono">${status.leaderId || 'Unknown'}</span>
                    </div>
                `;

                // Control Add Peer form visibility
                const addPeerForm = document.querySelector('.add-peer-form');
                if (addPeerForm) {
                    if (isLeader) {
                        addPeerForm.style.display = 'grid'; // Restore grid layout
                        // Remove any existing warning
                        const warning = document.getElementById('cluster-leader-warning');
                        if (warning) warning.remove();
                    } else {
                        addPeerForm.style.display = 'none';
                        if (!document.getElementById('cluster-leader-warning')) {
                            const warning = document.createElement('div');
                            warning.id = 'cluster-leader-warning';
                            warning.className = 'status-badge warning';
                            warning.style.marginTop = '1rem';
                            warning.style.width = '100%';
                            warning.style.justifyContent = 'center';
                            warning.textContent = 'Cluster management is only available on the Leader node.';
                            addPeerForm.parentNode.appendChild(warning);
                        }
                    }
                }

                if (tbody) {
                    tbody.innerHTML = '';
                    const nodes = status.nodes || status.peers;

                    // --- Visual Map Rendering ---
                    const visualMap = document.getElementById('cluster-visual-map');
                    if (visualMap) {
                        visualMap.innerHTML = '';
                        visualMap.className = 'cluster-map-container';

                        if (nodes && nodes.length > 0) {
                            nodes.forEach((p, idx) => {
                                let id, url, role, nodeStatus;
                                if (typeof p === 'string') {
                                    url = p; id = `node-${idx + 1}`; role = 'UNKNOWN'; nodeStatus = 'UNKNOWN';
                                } else {
                                    id = p.id || p._id || `node-${idx + 1}`;
                                    url = p.url || '';
                                    role = p.role || 'FOLLOWER';
                                    nodeStatus = p.status || 'ACTIVE';
                                }

                                let roleClass = 'node-follower';
                                if (role === 'LEADER') roleClass = 'node-leader';

                                let statusClass = '';
                                if (nodeStatus === 'INACTIVE') statusClass = 'node-inactive';
                                if (nodeStatus === 'PAUSED') statusClass = 'node-paused';

                                const nodeEl = document.createElement('div');
                                nodeEl.className = `node-circle ${roleClass} ${statusClass}`;
                                nodeEl.title = `URL: ${url}\nStatus: ${nodeStatus}`;
                                nodeEl.innerHTML = `
                                    <div class="node-id">${id.substring(0, 6)}</div>
                                    <div class="node-label">${role}</div>
                                    <div class="node-status-indicator" title="${nodeStatus}"></div>
                                `;
                                visualMap.appendChild(nodeEl);
                            });
                        } else {
                            // Show self at least
                            const nodeEl = document.createElement('div');
                            nodeEl.className = `node-circle ${isLeader ? 'node-leader' : 'node-follower'}`;
                            nodeEl.innerHTML = `<div class="node-id">${status.nodeId}</div><div class="node-label">Self</div>`;
                            visualMap.appendChild(nodeEl);
                        }
                    }
                    // ----------------------------

                    if (nodes && nodes.length > 0) {
                        nodes.forEach((p, idx) => {
                            // Handle both Object and String (legacy) formats
                            let id, url, desc, role, nodeStatus;

                            if (typeof p === 'string') {
                                url = p;
                                id = `node-${idx + 1}`;
                                desc = '';
                                role = 'UNKNOWN';
                                nodeStatus = 'UNKNOWN';
                            } else {
                                id = p.id || p._id || `node-${idx + 1}`;
                                url = p.url || '';
                                desc = p.description || '';
                                role = p.role || 'FOLLOWER';
                                nodeStatus = p.status || 'ACTIVE';
                            }

                            let actionBtns = '';
                            if (isLeader) {
                                if (id !== status.nodeId && !url.includes(status.nodeId)) { // Don't allow actions on self
                                    if (nodeStatus === 'PAUSED') {
                                        actionBtns += `<button onclick="app.resumeNode('${url}')" class="btn btn-sm" style="margin-right:5px; background: #10b981; color: white;">Resume</button>`;
                                    } else {
                                        // Add Pause button
                                        actionBtns += `<button onclick="app.pauseNode('${url}')" class="btn btn-sm" style="margin-right:5px; background: #f59e0b; color: white;">Pause</button>`;
                                    }
                                    // Remove button
                                    actionBtns += `<button onclick="app.removePeer('${id}', '${url}')" class="btn btn-sm btn-danger">Remove</button>`;
                                } else {
                                    actionBtns = '<span class="text-muted">Self</span>';
                                }
                            } else {
                                actionBtns = `<span class="text-gray-400" title="Only Leader can manage">read-only</span>`;
                            }

                            tbody.innerHTML += `
                                    <tr class="bg-white border-b dark:bg-gray-800 dark:border-gray-700">
                                        <td class="px-6 py-4 font-medium text-gray-900 whitespace-nowrap dark:text-white">
                                            ${id}
                                            ${isLeader && (id === status.nodeId || url.includes(status.nodeId)) ? ' (Self)' : ''}
                                        </td>
                                        <td class="px-6 py-4">${url}</td>
                                        <td class="px-6 py-4">
                                            <span class="badge ${role === 'LEADER' ? 'bg-success' : 'bg-primary'}">${role}</span>
                                        </td>
                                        <td class="px-6 py-4">${nodeStatus}</td>
                                        <td class="px-6 py-4">${desc}</td>
                                        <td class="px-6 py-4">
                                            ${actionBtns}
                                        </td>
                                    </tr>
                                 `;
                        });
                    } else {
                        tbody.innerHTML = '<tr><td colspan="3">No peers configured</td></tr>';
                    }
                }
            } else {
                content.textContent = 'Failed to fetch status';
            }
        } catch (e) {
            content.textContent = 'Error: ' + e.message;
        }
    },

    viewNodeMetrics(nodeId) {
        this.api('/cluster', 'GET').then(status => {
            if (status && status.nodes) {
                const node = status.nodes.find(n => (n.id === nodeId || n._id === nodeId));
                if (node) {
                    const metrics = node.metrics || { message: "No metrics available for this node at the moment." };
                    const metricsStr = JSON.stringify(metrics, null, 4);

                    const modal = document.createElement('div');
                    modal.className = 'metrics-modal';
                    modal.style.position = 'fixed';
                    modal.style.top = '0';
                    modal.style.left = '0';
                    modal.style.width = '100vw';
                    modal.style.height = '100vh';
                    modal.style.backgroundColor = 'rgba(0,0,0,0.85)';
                    modal.style.zIndex = '9999';
                    modal.style.display = 'flex';
                    modal.style.alignItems = 'center';
                    modal.style.justifyContent = 'center';
                    modal.style.padding = '20px';

                    modal.innerHTML = `
                        <div class="bg-gray-900 border border-gray-700 rounded-lg shadow-2xl w-full max-w-2xl flex flex-col" style="max-height: 85vh;">
                            <div class="p-4 border-b border-gray-700 flex justify-between items-center bg-gray-800">
                                <h3 class="text-xl font-bold text-white flex items-center gap-2">
                                    <span class="text-blue-400">📊</span> Metrics Detail: ${nodeId}
                                </h3>
                                <button onclick="this.closest('.metrics-modal').remove()" class="text-gray-400 hover:text-white text-2xl">&times;</button>
                            </div>
                            <div class="p-4 overflow-auto bg-black m-4 rounded font-mono text-sm text-green-400 flex-grow" style="box-shadow: inset 0 0 10px rgba(0,255,0,0.1);">
                                <pre>${metricsStr}</pre>
                            </div>
                            <div class="p-4 border-t border-gray-700 text-right bg-gray-800">
                                <button onclick="this.closest('.metrics-modal').remove()" class="px-6 py-2 bg-gray-600 text-white font-bold rounded hover:bg-gray-500 transition-colors">Close</button>
                            </div>
                        </div>
                    `;
                    document.body.appendChild(modal);
                } else {
                    this.showNotification('Node not found', 'error');
                }
            }
        });
    },

    async initFederatedView() {
        if (this.state.federatedInterval) clearInterval(this.state.federatedInterval);
        this.refreshFederatedStatus();
        this.state.federatedInterval = setInterval(() => this.refreshFederatedStatus(), 5000);
    },

    async refreshFederatedStatus() {
        if (!document.getElementById('federated-section').classList.contains('active') &&
            document.getElementById('federated-section').style.display === 'none') {
            clearInterval(this.state.federatedInterval);
            this.state.federatedInterval = null;
            return;
        }

        try {
            const res = await this.authenticatedFetch('/api/federated');
            if (res.ok) {
                const data = await res.json();
                this.renderFederatedStatus(data);
            } else {
                document.getElementById('federated-status-content').innerHTML = `
                    <div class="alert alert-error">Federated server not responding or not configured.</div>
                `;
            }
        } catch (e) {
            console.error('Federated status error:', e);
        }
    },

    renderFederatedStatus(data) {
        const statusContent = document.getElementById('federated-status-content');
        if (!statusContent) return;

        const leaderId = data.leaderId || 'None';
        const isFederatedLeader = data.isFederatedLeader;

        statusContent.innerHTML = `
            <div class="stat-group">
                <div class="stat-card">
                    <div class="stat-title">DB Cluster Leader</div>
                    <div class="stat-value text-primary">${leaderId}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-title">Federated Role</div>
                    <div class="stat-value ${isFederatedLeader ? 'text-success' : 'text-warning'}">
                        ${isFederatedLeader ? 'LEADER' : 'FOLLOWER'}
                    </div>
                </div>
                <div class="stat-card">
                    <div class="stat-title">Raft State</div>
                    <div class="stat-value">${data.raftState || 'Unknown'}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-title">Raft Term</div>
                    <div class="stat-value">${data.raftTerm || 0}</div>
                </div>
            </div>
        `;

        // Managed Nodes Table
        const nodesTbody = document.getElementById('federated-managed-nodes-tbody');
        if (nodesTbody) {
            nodesTbody.innerHTML = '';
            if (data.nodes) {
                data.nodes.forEach(node => {
                    const tr = document.createElement('tr');
                    const isLeader = node.id === data.leaderId;
                    const metrics = node.metrics || {};
                    const cpu = (metrics.cpuUsage !== undefined) ? metrics.cpuUsage + '%' : '0%';
                    const ram = (metrics.ramUsage !== undefined) ? metrics.ramUsage + '%' : '0%';
                    const disk = (metrics.diskUsage !== undefined) ? metrics.diskUsage + '%' : '0%';
                    const lastSeen = node.lastSeen ? new Date(node.lastSeen).toLocaleTimeString() : 'N/A';

                    tr.innerHTML = `
                        <td>
                            ${node.id || 'N/A'} 
                            ${isLeader ? '<span class="badge badge-warning" title="Leader" style="font-size: 10px; margin-left: 5px;">LEADER</span>' : ''}
                        </td>
                        <td><a href="${node.url}" target="_blank" class="text-primary hover:underline">${node.url}</a></td>
                        <td><span class="badge badge-${node.status === 'ACTIVE' ? 'success' : 'danger'}">${node.status}</span></td>
                        <td>${cpu}</td>
                        <td>${ram}</td>
                        <td>${disk}</td>
                        <td>${lastSeen}</td>
                    `;
                    nodesTbody.appendChild(tr);
                });
            }
        }

        // Federated Peers Table
        const serverTbody = document.getElementById('federated-servers-tbody');
        if (serverTbody) {
            serverTbody.innerHTML = '';
            const peers = data.raftPeers || [];
            const selfId = data.raftSelfId;
            const selfUrl = data.raftSelfUrl;
            const raftLeaderId = data.raftLeaderId;
            const raftLeaderUrl = data.raftLeaderUrl;

            const allPeers = [];
            if (selfUrl) allPeers.push({ url: selfUrl, id: selfId, isSelf: true });

            if (peers) {
                peers.forEach(peerUrl => {
                    const pid = data.raftPeerIds ? data.raftPeerIds[peerUrl] : 'unknown';
                    allPeers.push({ url: peerUrl, id: pid, isSelf: false });
                });
            }

            allPeers.forEach(peer => {
                const tr = document.createElement('tr');
                const isLeader = (peer.id && peer.id === raftLeaderId) || (peer.url && peer.url === raftLeaderUrl);
                const state = data.raftPeerStates ? (data.raftPeerStates[peer.url] || 'ACTIVE') : 'ACTIVE';

                tr.innerHTML = `
                   <td>${peer.id || '-'} ${peer.isSelf ? '<span class="text-muted">(You)</span>' : ''}</td>
                   <td><a href="${peer.url}" target="_blank" class="text-primary hover:underline">${peer.url}</a></td>
                   <td>
                        <span class="badge badge-${isLeader ? 'warning' : 'info'}">
                            ${isLeader ? 'LEADER' : 'FOLLOWER'}
                        </span>
                   </td>
                   <td><span class="badge badge-success">${state}</span></td>
                `;
                serverTbody.appendChild(tr);
            });
        }
    }

};

window.app = App;

window.addEventListener('DOMContentLoaded', () => {
    App.init();
});
