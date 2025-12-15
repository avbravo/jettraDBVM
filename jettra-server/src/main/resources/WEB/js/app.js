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
        hasMore: false // Simple pagination as we don't have total count easily efficiently without separate call
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
        const usersMenu = document.getElementById('menu-users');
        if (usersMenu) {
            if (this.state.role === 'admin') {
                usersMenu.classList.remove('hidden');
            } else {
                usersMenu.classList.add('hidden');
            }
        }
    },

    refresh() {
        if (this.state.currentDb && this.state.currentCol) {
            this.loadDocuments(this.state.currentDb, this.state.currentCol);
        }
        this.loadDatabases();
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
        this.state.token = null;
        this.state.currentDb = null;
        this.state.currentCol = null;
        this.state.role = null; // Clear role on logout
        localStorage.removeItem('jettra_token');
        localStorage.removeItem('jettra_role'); // Clear role from local storage
        this.showLogin();
        this.updateUsersMenuVisibility(); // Update menu visibility after logout
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
                        ${dbs.map(d => `<option value="${d}" ${d === preselectedDb ? 'selected' : ''}>${d}</option>`).join('')}
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
                    // Trigger download
                    window.location.href = `/api/backup/download?file=${data.file}&token=${this.state.token || localStorage.getItem('jettra_token')}`;

                    if (document.getElementById('backups-view').classList.contains('active')) {
                        this.loadBackups();
                    }
                } else {
                    this.showNotification('Backup failed', 'error');
                }
            });
    },

    promptBackupDatabase(db) {
        this.promptCreateBackup(db);
    },

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

    downloadBackup(file) {
        // Direct link download with auth is tricky. 
        // For now, assume admin logged in browser context works or we use a temporary token param logic if implemented.
        // Or simply:
        window.location.href = `/api/backup/download?file=${file}&token=${localStorage.getItem('jettra_token')}`;
        // Note: passing token in URL is not ideal security but common for file downloads
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
        this.renderSimpleForm('New Database Name', '', 'Create Database', (name) => {
            if (!name) return;
            this.authenticatedFetch('/api/dbs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name })
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
        dbs.forEach(db => {
            const li = document.createElement('li');

            const itemDiv = document.createElement('div');
            itemDiv.className = 'tree-item';
            itemDiv.innerHTML = `
                <span class="label">📂 ${db}</span>
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



    confirmDeleteDatabase(name) {
        if (confirm(`Delete database '${name}'? This cannot be undone.`)) {
            this.authenticatedFetch(`/api/dbs?name=${name}`, { method: 'DELETE' })
                .then(() => this.loadDatabases());
        }
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
                        document.getElementById('content-area').innerHTML = '';
                    }
                    this.loadDatabases();
                });
        }
    },

    // --- Documents ---
    selectCollection(db, col) {
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
        const container = document.getElementById('content-area');
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

            if (sectionId === 'cluster') {
                this.refreshClusterStatus();
            }
            if (sectionId === 'indexes') {
                this.initIndexesView();
            }
            if (sectionId === 'rules') {
                this.initRulesView();
            }
            if (sectionId === 'transactions') {
                this.initTxView();
            }
            return;
        }

        if (sectionId === 'query') {
            this.initQueryView();
            return;
        }

        if (sectionId === 'users') {
            this.initUsersView();
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
            const dbs = await res.json();
            dbs.forEach(db => {
                const opt = document.createElement('option');
                opt.value = db;
                opt.textContent = db;
                dbSelect.appendChild(opt);
            });
            // Auto Select current if avail
            if (this.state.currentDb) dbSelect.value = this.state.currentDb;
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
                            <input type="checkbox" name="allowed_dbs" value="${db}" style="margin-right:0.5rem;">
                            <span>${db}</span>
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
            const res = await this.authenticatedFetch('/api/users?id=' + id, { method: 'DELETE' });
            if (res.ok) {
                this.showNotification("User deleted", "success");
                this.loadUsers();
            } else {
                this.showNotification("Failed to delete", "error");
            }
        } catch (e) {
            this.showNotification("Error: " + e.message, "error");
        }
    },

    async refreshClusterStatus() {
        const content = document.getElementById('raft-status-content');
        content.innerHTML = 'Loading...';
        try {
            // Raft endpoints are not authenticated in current implementation or require different handling?
            // If they are under Engine/RaftService, they are just registered. 
            // Depending on implementation, they might not need auth token or might need it. 
            // Assuming they are public or cookie based, but let's try authenticatedFetch just in case they are wrapped?
            // Wait, RaftService registered raw paths. They might not check auth.
            // But let's use fetch directly for now if it fails use authenticatedFetch.

            // Actually, better to use fetch, as Raft endpoints are for internal/inter-node but exposing them to UI...
            // The user said "exposed endpoints".
            const res = await fetch('/raft/status');
            if (!res.ok) throw new Error('Failed to fetch status');

            const status = await res.json();

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

            // Render Peers
            const peersFunc = (peersMap) => {
                const tbody = document.getElementById('peers-table-body');
                if (!peersMap || Object.keys(peersMap).length === 0) {
                    tbody.innerHTML = '<tr><td colspan="3" class="px-6 py-4 text-center">No peers connected</td></tr>';
                    return;
                }

                let html = '';
                for (const [id, url] of Object.entries(peersMap)) {
                    html += `
                        <tr class="bg-white border-b dark:bg-gray-800 dark:border-gray-700">
                            <td class="px-6 py-4 font-medium text-gray-900 whitespace-nowrap dark:text-white">
                                ${id}
                            </td>
                            <td class="px-6 py-4">
                                ${url}
                            </td>
                            <td class="px-6 py-4">
                                <button onclick="app.removePeer('${id}')" class="font-medium text-red-600 dark:text-red-500 hover:underline">Remove</button>
                            </td>
                        </tr>
                     `;
                }
                tbody.innerHTML = html;
            };

            peersFunc(status.peers);

        } catch (e) {
            content.innerHTML = '<span class="text-red-500">Error loading status</span>';
            console.error(e);
        }
    },

    async addPeer() {
        const id = document.getElementById('peer-id').value;
        const url = document.getElementById('peer-url').value;
        if (!id || !url) return;

        try {
            const res = await fetch('/raft/peers', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ nodeId: id, url: url })
            }); // Note: using query params or body? RaftService.addPeer expects request body?
            // Wait, RaftService: rules.post("/raft/peers", this::addPeer)
            // RaftService.addPeer(ServerRequest req, ServerResponse res)
            // It parses body?
            // Assuming implementation:
            /*
            public void addPeer(ServerRequest req, ServerResponse res) {
                req.content().as(Map.class).then(map -> { ... });
            }
            */
            // So JSON body is correct.

            if (res.ok) {
                document.getElementById('peer-id').value = '';
                document.getElementById('peer-url').value = '';
                this.refreshClusterStatus();
            } else {
                alert('Failed to add peer');
            }
        } catch (e) {
            console.error(e);
            alert('Error adding peer');
        }
    },

    async removePeer(id) {
        if (!confirm('Remove peer ' + id + '?')) return;
        try {
            const res = await fetch(`/raft/peers?id=${id}`, {
                method: 'DELETE'
            }); // DELETE often uses query params

            if (res.ok) {
                this.refreshClusterStatus();
            } else {
                alert('Failed to remove peer');
            }
        } catch (e) {
            console.error(e);
            alert('Error removing peer');
        }
    },

    async forceLeader() {
        if (!confirm('DANGEROUS: Forcing this node to be leader can cause split-brain if another leader exists. Continue?')) return;
        try {
            const res = await fetch('/raft/force-leader', { method: 'POST' });
            if (res.ok) {
                this.refreshClusterStatus();
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

    async createIndex() {
        const db = document.getElementById('idx-db-select').value;
        const col = document.getElementById('idx-col-select').value;
        const field = document.getElementById('idx-field').value;
        const type = document.getElementById('idx-type').value;
        const sequential = document.getElementById('idx-sequential').checked;

        if (!db || !col || !field) {
            alert("Please fill all fields");
            return;
        }

        try {
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
                alert('Index created');
                document.getElementById('idx-field').value = '';
                document.getElementById('idx-sequential').checked = false; // Reset
                this.loadIndexes();
            } else {
                alert('Failed to create index');
            }
        } catch (e) {
            console.error(e);
            alert('Error creating index');
        }
    },

    async deleteIndex(db, col, field) {
        if (!confirm(`Delete index on '${field}'?`)) return;
        try {
            const res = await this.authenticatedFetch(`/api/index?db=${db}&col=${col}&field=${field}`, { method: 'DELETE' });
            if (res.ok) {
                this.loadIndexes();
            } else {
                alert('Failed to delete index');
            }
        } catch (e) {
            console.error(e);
            alert('Error deleting index');
        }
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
                opt.value = db;
                opt.textContent = db;
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
                    opt.value = db;
                    opt.textContent = db;
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
                    opt.value = db;
                    opt.textContent = db;
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
    }
};

window.app = App;

window.addEventListener('DOMContentLoaded', () => {
    App.init();
});
