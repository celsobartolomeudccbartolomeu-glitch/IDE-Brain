(function () {
  'use strict';

  const KEYWORDS = new Set([
    'int', 'float', 'char', 'bool', 'soma', 'sub', 'multiplica', 'div',
    'print', 'if', 'else', 'endif', 'while', 'endwhile', 'and', 'or', 'not',
  ]);
  const DOT_COMMANDS = new Set([
    'IA.programa', 'IA.digitalwrite.line', 'Brain.data_types', 'see.lines',
    'show.vars', '.run', '.sair_prog', '.del', '.version', '.help',
    '.screnclear', '.scanln.Br', '.clear_var',
  ]);

  const state = {
    mode: 'program',
    activeFile: 'ola_brain.brain',
    savedSnapshot: '',
    isRunning: false,
    fontSize: Number(localStorage.getItem('brain_font_size')) || 14,
    theme: localStorage.getItem('brain_theme') || 'dark',
  };

  let worker = null;
  let prevEditorText = '';

  const $ = (id) => document.getElementById(id);
  const codeEditor = $('code-editor');
  const codeHighlight = $('code-highlight');
  const lineNumbers = $('line-numbers');
  const consoleOutput = $('console-output');
  const btnRun = $('btn-run');
  const btnStop = $('btn-stop');
  const scanlnForm = $('scanln-form');
  const scanlnInput = $('scanln-input');
  const scanlnLabel = $('scanln-label');
  const replControls = $('repl-controls');
  const replForm = $('repl-form');
  const replInput = $('repl-input');
  const replPrompt = $('repl-prompt');
  const progBadge = $('prog-buffer-badge');
  const fileModal = $('file-modal');
  const fileList = $('file-list');

  function escapeHtml(str) {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  function highlightBrain(source) {
    let out = '';
    let i = 0;
    const n = source.length;
    while (i < n) {
      const c = source[i];
      if (c === '#' || (c === '/' && source[i + 1] === '/')) {
        let start = i;
        while (i < n && source[i] !== '\n') i++;
        out += `<span class="tok-comment">${escapeHtml(source.slice(start, i))}</span>`;
        continue;
      }
      if (c === '"' || c === "'") {
        const q = c;
        let start = i++;
        while (i < n && source[i] !== '\n') {
          if (source[i] === '\\' && i + 1 < n) {
            i += 2;
            continue;
          }
          if (source[i] === q) {
            i++;
            break;
          }
          i++;
        }
        out += `<span class="tok-string">${escapeHtml(source.slice(start, i))}</span>`;
        continue;
      }
      if (/[0-9]/.test(c) && (i === 0 || !/[A-Za-z0-9_]/.test(source[i - 1]))) {
        let start = i;
        while (i < n && /[0-9.]/.test(source[i])) i++;
        out += `<span class="tok-number">${escapeHtml(source.slice(start, i))}</span>`;
        continue;
      }
      if (c === '.' && i + 1 < n && /[A-Za-z]/.test(source[i + 1])) {
        let start = i++;
        while (i < n && /[A-Za-z0-9_.]/.test(source[i])) i++;
        const word = source.slice(start, i);
        out += DOT_COMMANDS.has(word)
          ? `<span class="tok-dot">${escapeHtml(word)}</span>`
          : escapeHtml(word);
        continue;
      }
      if (/[A-Za-z_]/.test(c)) {
        let start = i;
        while (i < n && /[A-Za-z0-9_.]/.test(source[i])) i++;
        const word = source.slice(start, i);
        if (DOT_COMMANDS.has(word)) out += `<span class="tok-dot">${escapeHtml(word)}</span>`;
        else if (KEYWORDS.has(word)) out += `<span class="tok-keyword">${escapeHtml(word)}</span>`;
        else out += escapeHtml(word);
        continue;
      }
      out += escapeHtml(c);
      i++;
    }
    return out + (source.endsWith('\n') ? ' ' : '');
  }

  function syncEditorVisuals() {
    const text = codeEditor.value;
    prevEditorText = text;
    codeHighlight.innerHTML = highlightBrain(text);
    const lines = Math.max(1, text.split('\n').length);
    let nums = '';
    for (let i = 1; i <= lines; i++) nums += (i > 1 ? '\n' : '') + i;
    lineNumbers.textContent = nums;
    $('line-count').textContent = `${lines} linhas`;
    $('unsaved-dot').classList.toggle('hidden', text === state.savedSnapshot);
  }

  function appendLog(text, level = 'output') {
    const p = document.createElement('p');
    p.className = `log-line log-${level}`;
    p.textContent = text;
    consoleOutput.appendChild(p);
    while (consoleOutput.childElementCount > 800) {
      consoleOutput.removeChild(consoleOutput.firstElementChild);
    }
    consoleOutput.scrollTop = consoleOutput.scrollHeight;
  }

  function setRunning(running) {
    state.isRunning = running;
    btnRun.disabled = running;
    btnStop.disabled = !running;
    if (!running) {
      scanlnForm.classList.add('hidden');
    }
  }

  function spawnWorker() {
    if (worker) worker.terminate();
    worker = new Worker('./brain-worker.js');
    worker.onmessage = (event) => {
      const msg = event.data;
      if (!msg) return;
      if (msg.type === 'output') {
        appendLog(msg.text, msg.level);
      } else if (msg.type === 'clear_screen') {
        consoleOutput.innerHTML = '';
      } else if (msg.type === 'input_request') {
        scanlnLabel.textContent = `Digite ${msg.varName}:`;
        scanlnInput.value = '';
        scanlnForm.classList.remove('hidden');
        scanlnInput.focus();
      } else if (msg.type === 'program_mode') {
        progBadge.textContent = `PROG:${msg.count}`;
        progBadge.classList.toggle('hidden', !msg.active);
        replPrompt.textContent = msg.active ? '...' : '>>>';
      } else if (msg.type === 'done') {
        setRunning(false);
      }
    };
    worker.onerror = (err) => {
      appendLog(`Erro no Worker: ${err.message}`, 'error');
      setRunning(false);
    };
  }

  function applyThemeAndFont() {
    document.documentElement.setAttribute('data-theme', state.theme);
    document.documentElement.style.setProperty('--font-size', `${state.fontSize}px`);
    $('btn-theme').textContent = state.theme === 'dark' ? '☀️' : '🌙';
    localStorage.setItem('brain_theme', state.theme);
    localStorage.setItem('brain_font_size', String(state.fontSize));
  }

  function setMode(mode) {
    state.mode = mode;
    $('btn-mode-program').classList.toggle('active', mode === 'program');
    $('btn-mode-repl').classList.toggle('active', mode === 'repl');
    $('editor-panel').classList.toggle('hidden', mode === 'repl');
    $('workspace').classList.toggle('repl-only', mode === 'repl');
    replControls.classList.toggle('hidden', mode !== 'repl');
    $('console-title').textContent = mode === 'repl' ? 'REPL (>>>)' : 'Consola';
  }

  async function refreshFileList() {
    const files = await window.BrainIDB.listFiles();
    fileList.innerHTML = '';
    if (files.length === 0) {
      fileList.innerHTML = '<p class="muted">Nenhum ficheiro .brain guardado.</p>';
      return;
    }
    files.forEach((file) => {
      const row = document.createElement('div');
      row.className = `file-row ${file.name === state.activeFile ? 'active' : ''}`;

      const openBtn = document.createElement('button');
      openBtn.type = 'button';
      openBtn.className = 'file-open-btn';
      openBtn.textContent = `${file.name} (${file.size} B)`;
      openBtn.onclick = async () => {
        const rec = await window.BrainIDB.openFile(file.name);
        if (rec) {
          state.activeFile = rec.name;
          state.savedSnapshot = rec.content;
          codeEditor.value = rec.content;
          $('active-filename').textContent = rec.name;
          localStorage.setItem('brain_last_file', rec.name);
          syncEditorVisuals();
          setMode('program');
          fileModal.classList.add('hidden');
        }
      };

      const delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'small-btn';
      delBtn.textContent = 'Apagar';
      delBtn.onclick = async () => {
        await window.BrainIDB.deleteFile(file.name);
        await refreshFileList();
      };

      row.appendChild(openBtn);
      row.appendChild(delBtn);
      fileList.appendChild(row);
    });
  }

  async function saveCurrent(silent = false) {
    const record = await window.BrainIDB.saveFile(state.activeFile, codeEditor.value);
    state.activeFile = record.name;
    state.savedSnapshot = record.content;
    $('active-filename').textContent = record.name;
    localStorage.setItem('brain_last_file', record.name);
    syncEditorVisuals();
    if (!silent) appendLog(`Ficheiro '${record.name}' guardado no IndexedDB.`, 'system');
  }

  function insertAtCursor(el, token) {
    const text = token === 'tab' ? '    ' : token;
    const start = el.selectionStart ?? el.value.length;
    const end = el.selectionEnd ?? el.value.length;
    el.value = el.value.slice(0, start) + text + el.value.slice(end);
    const nextPos = start + text.length;
    el.setSelectionRange(nextPos, nextPos);
    el.focus();
  }

  function stripCommentForIndent(rawLine) {
    const s = String(rawLine || '');
    let inQuote = null;
    for (let i = 0; i < s.length; i++) {
      const c = s[i];
      if (inQuote) {
        if (c === '\\' && i + 1 < s.length) {
          i++;
          continue;
        }
        if (c === inQuote) inQuote = null;
        continue;
      }
      if (c === '"' || c === "'") {
        inQuote = c;
        continue;
      }
      if (c === '#' || (c === '/' && i + 1 < s.length && s[i + 1] === '/')) {
        return s.slice(0, i).trim();
      }
    }
    return s.trim();
  }

  function isBlockOpener(stripped) {
    return (
      stripped === 'if' ||
      stripped.startsWith('if ') ||
      stripped === 'while' ||
      stripped.startsWith('while ')
    );
  }

  function isBlockCloser(stripped) {
    return stripped === 'endif' || stripped === 'endwhile';
  }

  function isElseLine(stripped) {
    return stripped === 'else' || stripped === 'else:';
  }

  function computeNextLineIndent(textBeforeNewline) {
    const lines = textBeforeNewline.split('\n');
    const currentLine = lines[lines.length - 1] || '';
    const leadingMatch = currentLine.match(/^[ \t]*/);
    const currentLeading = leadingMatch ? leadingMatch[0] : '';
    let currentCols = 0;
    for (const ch of currentLeading) {
      currentCols += ch === '\t' ? 4 : 1;
    }

    let depth = 0;
    for (const rawLine of lines) {
      const stripped = stripCommentForIndent(rawLine);
      if (isBlockOpener(stripped)) depth++;
      else if (isBlockCloser(stripped)) depth = Math.max(0, depth - 1);
    }

    const lastStripped = stripCommentForIndent(currentLine);
    if (isBlockOpener(lastStripped) || isElseLine(lastStripped)) {
      const targetCols = Math.max(depth * 4, currentCols + 4);
      return ' '.repeat(targetCols);
    }
    if (isBlockCloser(lastStripped)) {
      return ' '.repeat(depth * 4);
    }
    return currentLeading;
  }

  // Event bindings
  codeEditor.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
      e.preventDefault();
      const start = codeEditor.selectionStart ?? codeEditor.value.length;
      const end = codeEditor.selectionEnd ?? codeEditor.value.length;
      const indent = computeNextLineIndent(codeEditor.value.slice(0, start));
      const inserted = '\n' + indent;
      codeEditor.value =
        codeEditor.value.slice(0, start) + inserted + codeEditor.value.slice(end);
      const nextPos = start + inserted.length;
      codeEditor.setSelectionRange(nextPos, nextPos);
      prevEditorText = codeEditor.value;
      syncEditorVisuals();
    }
  });

  codeEditor.addEventListener('input', () => {
    const val = codeEditor.value;
    const cursor = codeEditor.selectionStart ?? val.length;
    if (
      codeEditor.selectionStart === codeEditor.selectionEnd &&
      val.length === prevEditorText.length + 1 &&
      cursor > 0 &&
      val[cursor - 1] === '\n' &&
      val.slice(0, cursor - 1) === prevEditorText.slice(0, cursor - 1) &&
      val.slice(cursor) === prevEditorText.slice(cursor - 1)
    ) {
      const indent = computeNextLineIndent(val.slice(0, cursor - 1));
      if (indent.length > 0) {
        codeEditor.value = val.slice(0, cursor) + indent + val.slice(cursor);
        const nextPos = cursor + indent.length;
        codeEditor.setSelectionRange(nextPos, nextPos);
      }
    }
    prevEditorText = codeEditor.value;
    syncEditorVisuals();
  });

  codeEditor.parentElement.addEventListener('scroll', () => {
    lineNumbers.scrollTop = codeEditor.parentElement.scrollTop;
  });

  $('aux-bar').addEventListener('click', (e) => {
    const btn = e.target.closest('.aux-key');
    if (!btn) return;
    insertAtCursor(codeEditor, btn.dataset.insert);
    prevEditorText = codeEditor.value;
    syncEditorVisuals();
  });

  btnRun.addEventListener('click', async () => {
    if (state.isRunning) return;
    if (state.mode === 'repl') {
      replForm.requestSubmit();
      return;
    }
    await saveCurrent(true);
    setRunning(true);
    appendLog(`▶ A executar ${state.activeFile}...`, 'system');
    worker.postMessage({ type: 'run_program', code: codeEditor.value });
  });

  btnStop.addEventListener('click', () => {
    if (!state.isRunning) return;
    spawnWorker();
    setRunning(false);
    appendLog('■ Execução interrompida pelo utilizador.', 'warning');
  });

  $('btn-clear').addEventListener('click', () => {
    consoleOutput.innerHTML = '';
  });

  scanlnForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const val = scanlnInput.value;
    appendLog(`${scanlnLabel.textContent} ${val}`, 'prompt');
    scanlnForm.classList.add('hidden');
    worker.postMessage({ type: 'input_response', value: val });
  });

  replForm.addEventListener('submit', (e) => {
    e.preventDefault();
    if (state.isRunning) return;
    const line = replInput.value.trim();
    if (!line) return;
    appendLog(`${replPrompt.textContent} ${line}`, 'prompt');
    replInput.value = '';
    setRunning(true);
    worker.postMessage({ type: 'run_repl', line });
  });

  replControls.addEventListener('click', (e) => {
    const btn = e.target.closest('.chip-btn');
    if (!btn || state.isRunning) return;
    replInput.value = btn.dataset.cmd;
    replForm.requestSubmit();
  });

  $('btn-mode-program').addEventListener('click', () => setMode('program'));
  $('btn-mode-repl').addEventListener('click', () => setMode('repl'));
  $('btn-font-dec').addEventListener('click', () => {
    state.fontSize = Math.max(11, state.fontSize - 1);
    applyThemeAndFont();
  });
  $('btn-font-inc').addEventListener('click', () => {
    state.fontSize = Math.min(22, state.fontSize + 1);
    applyThemeAndFont();
  });
  $('btn-theme').addEventListener('click', () => {
    state.theme = state.theme === 'dark' ? 'light' : 'dark';
    applyThemeAndFont();
  });

  $('btn-save-quick').addEventListener('click', () => saveCurrent(false));
  $('btn-files').addEventListener('click', async () => {
    await refreshFileList();
    fileModal.classList.remove('hidden');
  });
  $('btn-close-modal').addEventListener('click', () => fileModal.classList.add('hidden'));
  $('btn-save-file').addEventListener('click', async () => {
    await saveCurrent(false);
    await refreshFileList();
  });
  $('btn-new-file').addEventListener('click', async () => {
    const name = `programa_${Date.now().toString().slice(-4)}.brain`;
    const rec = await window.BrainIDB.saveFile(name, 'IA.programa\nprint "Olá!"\n.run');
    state.activeFile = rec.name;
    state.savedSnapshot = rec.content;
    codeEditor.value = rec.content;
    $('active-filename').textContent = rec.name;
    syncEditorVisuals();
    await refreshFileList();
  });
  $('btn-save-as').addEventListener('click', async () => {
    const base = state.activeFile.replace(/\.brain$/i, '');
    const copyName = `${base}_copia.brain`;
    const rec = await window.BrainIDB.saveFile(copyName, codeEditor.value);
    state.activeFile = rec.name;
    state.savedSnapshot = rec.content;
    $('active-filename').textContent = rec.name;
    syncEditorVisuals();
    await refreshFileList();
  });
  $('btn-export-file').addEventListener('click', () => {
    window.BrainIDB.exportFile(state.activeFile, codeEditor.value);
  });
  $('input-import-file').addEventListener('change', async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const rec = await window.BrainIDB.importFile(file);
    state.activeFile = rec.name;
    state.savedSnapshot = rec.content;
    codeEditor.value = rec.content;
    $('active-filename').textContent = rec.name;
    syncEditorVisuals();
    await refreshFileList();
    e.target.value = '';
  });

  async function init() {
    applyThemeAndFont();
    spawnWorker();
    const files = await window.BrainIDB.seedDefaultsIfEmpty();
    const last = localStorage.getItem('brain_last_file') || 'ola_brain.brain';
    const initial = files.find((f) => f.name === last) || files[0];
    if (initial) {
      state.activeFile = initial.name;
      state.savedSnapshot = initial.content;
      codeEditor.value = initial.content;
      $('active-filename').textContent = initial.name;
    }
    syncEditorVisuals();
    appendLog('Brain Language v1.10_test-fix3 — PWA pronta (Web Worker + IndexedDB).', 'system');

    if ('serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('./sw.js')
        .then(() => {
          $('offline-badge').textContent = 'Offline ✓';
        })
        .catch(() => {
          $('offline-badge').textContent = 'Local';
        });
    }
  }

  init();
})();
