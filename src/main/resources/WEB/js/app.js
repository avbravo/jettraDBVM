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
            }).then(() => {
                this.closeInputModal();
                this.loadDatabases();
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
            }).then(() => {
                this.closeInputModal();
                this.loadDatabases();
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
        const container = document.getElementById('content-area');
        document.getElementById('collection-actions').style.display = 'none';

        container.innerHTML = `
            <div class="center-view-container">
                 <div class="center-view-header">
                    <h3>${title}</h3>
                </div>
                <div class="form-group">
                    <label>Name</label>
                    <input type="text" id="center-input-value" value="${initialValue}" required>
                </div>
                <div class="center-view-footer">
                    <button class="btn btn-secondary" onclick="App.refresh()">Cancel</button>
                    <button class="btn btn-primary" id="center-input-confirm">${confirmLabel}</button>
                </div>
            </div>
        `;

        document.getElementById('center-input-confirm').addEventListener('click', () => {
            callback(document.getElementById('center-input-value').value);
        });
    },

    openDocEditor(id = null) {
        const container = document.getElementById('content-area');
        document.getElementById('collection-actions').style.display = 'none';

        let initialContent = '{\n  \n}';
        let isEdit = false;

        if (id) {
            const doc = this.state.docs.find(d => (d._id || d.id) === id);
            if (doc) {
                initialContent = JSON.stringify(doc, null, 2);
                isEdit = true;
            }
        }

        container.innerHTML = `
             <div class="center-view-container" style="max-width: 900px;">
                 <div class="center-view-header">
                    <h3>${isEdit ? 'Edit Document' : 'New Document'}</h3>
                </div>
                 <div class="form-group">
                    <label>ID (Optional for new)</label>
                    <input type="text" id="center-doc-id" value="${id || ''}" ${isEdit ? 'disabled' : ''}>
                </div>
                <div class="form-group" style="height: 400px;">
                    <label>Content (JSON)</label>
                    <textarea id="center-doc-content"
                        style="width: 100%; height: 100%; background: var(--bg-primary); color: var(--text-primary); border: 1px solid var(--border-color); padding: 10px; font-family: monospace;">${initialContent}</textarea>
                </div>
                <div class="center-view-footer">
                    <button class="btn btn-secondary" onclick="App.loadDocuments('${this.state.currentDb}', '${this.state.currentCol}'); document.getElementById('collection-actions').style.display = 'flex';">Cancel</button>
                    <button class="btn btn-primary" id="center-save-doc">Save Document</button>
                </div>
             </div>
        `;

        document.getElementById('center-save-doc').addEventListener('click', () => {
            this.saveDocumentFromCenter(document.getElementById('center-doc-id').value, document.getElementById('center-doc-content').value);
        });
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
        } catch (e) {
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
    }
};

window.addEventListener('DOMContentLoaded', () => {
    App.init();
});
