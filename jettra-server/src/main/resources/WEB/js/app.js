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

    init() {
        // Theme Init
        if (localStorage.getItem('jettra_theme') === 'light') {
            document.body.classList.add('light-theme');
        }

        if (this.state.token) {
            this.showDashboard();
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
                    alert('Login successful but no token received');
                    return;
                }
                this.state.token = data.token;
                localStorage.setItem('jettra_token', data.token);
                document.getElementById('user-display').textContent = user;
                this.showDashboard();
            } else {
                const text = await res.text();
                alert('Login failed: ' + res.status + ' ' + text);
                document.getElementById('login-error').textContent = 'Invalid credentials';
            }
        } catch (e) {
            alert('Login error: ' + e.message);
            document.getElementById('login-error').textContent = 'Connection error';
        }
    },

    logout() {
        this.state.token = null;
        this.state.currentDb = null;
        this.state.currentCol = null;
        localStorage.removeItem('jettra_token');
        this.showLogin();
    },

    showLogin() {
        document.getElementById('login-view').classList.add('active');
        document.getElementById('dashboard-view').classList.remove('active');
    },

    showDashboard() {
        document.getElementById('login-view').classList.remove('active');
        document.getElementById('dashboard-view').classList.add('active');
        this.loadDatabases();
    },

    openPasswordView() {
        this.renderView('password');
    },

    async changePassword(p1, p2) {
        if (p1 !== p2) {
            alert("Passwords do not match");
            return;
        }

        try {
            const res = await this.authenticatedFetch('/api/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ newPassword: p1 })
            });
            if (res.ok) {
                alert("Password updated");
                this.refresh(); // Back to dashboard home
            } else {
                alert("Failed to update password");
            }
        } catch (e) {
            console.error(e);
            alert("Error updating password");
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
                <div class="tree-actions">
                    <button class="tree-btn" title="Create Collection" onclick="App.promptCreateCollection('${db}')">+</button>
                    <button class="tree-btn" title="Rename" onclick="App.promptRenameDatabase('${db}')">✎</button>
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

    // --- DB/Col Operations ---
    promptCreateDatabase() {
        this.renderSimpleForm('New Database Name', '', 'Create Database', (name) => {
            if (!name) return;
            this.authenticatedFetch('/api/dbs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name })
            }).then(res => {
                if (res.ok) {
                    this.closeInputModal();
                    this.loadDatabases();
                } else {
                    res.text().then(t => alert('Failed to create DB: ' + t));
                }
            });
        });
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
                    alert('Database renamed successfully');
                    this.closeInputModal();
                    this.loadDatabases();
                } else {
                    alert('Failed to rename database');
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
                document.getElementById('content-area').innerHTML = `<div class="empty-state"><h2>Collection Created</h2></div>`;
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

    closeInputModal() {
        const overlay = document.getElementById('modal-overlay');
        overlay.classList.add('hidden');
        overlay.style.display = 'none';
        document.getElementById('modal-body').innerHTML = ''; // Clean up
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
            alert("Invalid JSON");
            return;
        }

        try {
            if (this.state.docs.some(d => (d._id || d.id) === id)) {
                await this.authenticatedFetch(`/api/doc?db=${this.state.currentDb}&col=${this.state.currentCol}&id=${id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc)
                });
            } else {
                await this.authenticatedFetch(`/api/doc?db=${this.state.currentDb}&col=${this.state.currentCol}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc)
                });
            }
            // Return to list
            this.selectCollection(this.state.currentDb, this.state.currentCol);
            this.closeInputModal(); // Fix: Close modal on success
        } catch (e) {
            console.error(e);
            alert('Failed to save document');
        }
    },

    async deleteDocument(id) {
        if (!confirm('Are you sure?')) return;
        try {
            await this.authenticatedFetch(`/api/doc?db=${this.state.currentDb}&col=${this.state.currentCol}&id=${id}`, {
                method: 'DELETE'
            });
            this.loadDocuments(this.state.currentDb, this.state.currentCol);
        } catch (e) {
            alert('Failed to delete');
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

            if (sectionId === 'cluster') {
                this.refreshClusterStatus();
            }
            if (sectionId === 'indexes') {
                this.initIndexesView();
            }
        } else {
            console.error('Target section not found:', targetId);
            // Fallback
            const dbSection = document.getElementById('dashboard-section');
            if (dbSection) {
                dbSection.classList.remove('hidden');
                dbSection.style.display = 'block';
            }
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
    }
};

window.app = App;

window.addEventListener('DOMContentLoaded', () => {
    App.init();
});
