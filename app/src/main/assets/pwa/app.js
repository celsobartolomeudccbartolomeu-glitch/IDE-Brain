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
    tab: 'editor', // 'editor' | 'console' | 'repl'
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
  const newFileDialog = $('new-file-dialog');
  const newFileForm = $('new-file-form');
  const newFileNameInput = $('new-file-name-input');

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
    $('line-count').textContent = `${lines} ln`;
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
    $('tab-console').textContent = running ? 'Consola •' : 'Consola';
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

  function setTab(tab) {
    state.tab = tab;
    $('tab-editor').classList.toggle('active', tab === 'editor');
    $('tab-console').classList.toggle('active', tab === 'console');
    $('tab-repl').classList.toggle('active', tab === 'repl');
    $('editor-panel').classList.toggle('hidden', tab !== 'editor');
    $('console-panel').classList.toggle('hidden', tab === 'editor');
    replControls.classList.toggle('hidden', tab !== 'repl');
    btnRun.classList.toggle('hidden', tab !== 'console');
    $('console-title').textContent = tab === 'repl' ? 'REPL (>>>)' : 'Consola';
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
          setTab('editor');
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

  function openNewFileDialog() {
    fileModal.classList.add('hidden');
    newFileNameInput.value = `programa_${Date.now().toString().slice(-3)}.brain`;
    newFileDialog.classList.remove('hidden');
    requestAnimationFrame(() => {
      newFileNameInput.focus();
      newFileNameInput.select();
    });
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

  const AC_CATALOG = [
    { id: 'cmd_print', label: 'print', insertText: 'print ', kind: 'CMD', detail: '<expr>', cursorBack: 0 },
    { id: 'cmd_digitalwrite', label: 'IA.digitalwrite.line', insertText: 'IA.digitalwrite.line ""', kind: 'CMD', detail: '"texto"', cursorBack: 1 },
    { id: 'cmd_scanln', label: '.scanln.Br', insertText: '.scanln.Br ', kind: 'CMD', detail: '<var>', cursorBack: 0 },
    { id: 'cmd_programa', label: 'IA.programa', insertText: 'IA.programa', kind: 'CMD', detail: 'início', cursorBack: 0 },
    { id: 'cmd_run', label: '.run', insertText: '.run', kind: 'CMD', detail: 'executar', cursorBack: 0 },
    { id: 'cmd_show_vars', label: 'show.vars', insertText: 'show.vars', kind: 'CMD', detail: 'variáveis', cursorBack: 0 },
    { id: 'cmd_clear_var', label: '.clear_var', insertText: '.clear_var ', kind: 'CMD', detail: '<var>', cursorBack: 0 },
    { id: 'cmd_screnclear', label: '.screnclear', insertText: '.screnclear', kind: 'CMD', detail: 'limpar', cursorBack: 0 },
    { id: 'cmd_data_types', label: 'Brain.data_types', insertText: 'Brain.data_types', kind: 'CMD', detail: 'tipos', cursorBack: 0 },
    { id: 'cmd_see_lines', label: 'see.lines', insertText: 'see.lines', kind: 'CMD', detail: 'linhas', cursorBack: 0 },
    { id: 'cmd_del', label: '.del', insertText: '.del ', kind: 'CMD', detail: '<num>', cursorBack: 0 },
    { id: 'cmd_sair_prog', label: '.sair_prog', insertText: '.sair_prog', kind: 'CMD', detail: 'cancelar', cursorBack: 0 },
    { id: 'cmd_help', label: '.help', insertText: '.help', kind: 'CMD', detail: 'ajuda', cursorBack: 0 },
    { id: 'cmd_version', label: '.version', insertText: '.version', kind: 'CMD', detail: 'versão', cursorBack: 0 },
    { id: 'kw_if', label: 'if', insertText: 'if ', kind: 'KW', detail: '<condição>', cursorBack: 0 },
    { id: 'snip_if_block', label: 'if ... endif', insertText: 'if \n    \nendif', kind: 'BLOCO', detail: 'bloco', cursorBack: 11 },
    { id: 'kw_else', label: 'else', insertText: 'else', kind: 'KW', detail: 'senão', cursorBack: 0 },
    { id: 'kw_endif', label: 'endif', insertText: 'endif', kind: 'KW', detail: 'fim if', cursorBack: 0 },
    { id: 'kw_while', label: 'while', insertText: 'while ', kind: 'KW', detail: '<condição>', cursorBack: 0 },
    { id: 'snip_while_block', label: 'while ... endwhile', insertText: 'while \n    \nendwhile', kind: 'BLOCO', detail: 'ciclo', cursorBack: 14 },
    { id: 'kw_endwhile', label: 'endwhile', insertText: 'endwhile', kind: 'KW', detail: 'fim while', cursorBack: 0 },
    { id: 'type_int', label: 'int', insertText: 'int ', kind: 'TIPO', detail: 'inteiro', cursorBack: 0 },
    { id: 'type_float', label: 'float', insertText: 'float ', kind: 'TIPO', detail: 'decimal', cursorBack: 0 },
    { id: 'type_char', label: 'char', insertText: 'char ', kind: 'TIPO', detail: '1 car.', cursorBack: 0 },
    { id: 'type_bool', label: 'bool', insertText: 'bool ', kind: 'TIPO', detail: 'lógico', cursorBack: 0 },
    { id: 'op_soma', label: 'soma', insertText: 'soma ', kind: 'OP', detail: 'a b', cursorBack: 0 },
    { id: 'op_sub', label: 'sub', insertText: 'sub ', kind: 'OP', detail: 'a b', cursorBack: 0 },
    { id: 'op_multiplica', label: 'multiplica', insertText: 'multiplica ', kind: 'OP', detail: 'a b', cursorBack: 0 },
    { id: 'op_div', label: 'div', insertText: 'div ', kind: 'OP', detail: 'a b', cursorBack: 0 },
    { id: 'op_and', label: 'and', insertText: 'and ', kind: 'OP', detail: 'e', cursorBack: 0 },
    { id: 'op_or', label: 'or', insertText: 'or ', kind: 'OP', detail: 'ou', cursorBack: 0 },
    { id: 'op_not', label: 'not', insertText: 'not ', kind: 'OP', detail: 'não', cursorBack: 0 },
    { id: 'bool_true', label: 'true', insertText: 'true', kind: 'BOOL', detail: 'verdadeiro', cursorBack: 0 },
    { id: 'bool_false', label: 'false', insertText: 'false', kind: 'BOOL', detail: 'falso', cursorBack: 0 },
  ];

  const AC_ARG_TRIGGERS = new Set([
    'print', 'if', 'while', 'soma', 'sub', 'multiplica', 'div',
    '.scanln.Br', '.clear_var', 'IA.digitalwrite.line',
  ]);

  function isInsideCommentOrString(lineBeforeCursor) {
    let inQuote = null;
    for (let i = 0; i < lineBeforeCursor.length; i++) {
      const c = lineBeforeCursor[i];
      if (inQuote) {
        if (c === '\\' && i + 1 < lineBeforeCursor.length) { i++; continue; }
        if (c === inQuote) inQuote = null;
        continue;
      }
      if (c === '"' || c === "'") { inQuote = c; continue; }
      if (c === '#' || (c === '/' && i + 1 < lineBeforeCursor.length && lineBeforeCursor[i + 1] === '/')) {
        return true;
      }
    }
    return inQuote !== null;
  }

  function findTokenStart(lineBeforeCursor) {
    let i = lineBeforeCursor.length;
    while (i > 0 && /[A-Za-z0-9_.]/.test(lineBeforeCursor[i - 1])) i--;
    return i;
  }

  function extractDeclaredVars(source) {
    const vars = new Set();
    for (const rawLine of String(source || '').split('\n')) {
      const clean = stripCommentForIndent(rawLine);
      if (!clean) continue;
      let m = clean.match(/^(?:int|float|char|bool)\s+([A-Za-z_]\w*)\s*=/);
      if (m) vars.add(m[1]);
      m = clean.match(/^\.scanln\.Br\s+([A-Za-z_]\w*)/);
      if (m) vars.add(m[1]);
      m = clean.match(/^([A-Za-z_]\w*)\s*=(?!=)/);
      if (m && !['int', 'float', 'char', 'bool'].includes(m[1])) vars.add(m[1]);
    }
    return Array.from(vars);
  }

  function analyzeOpenBlocks(textBeforeLine) {
    const stack = [];
    for (const rawLine of textBeforeLine.split('\n')) {
      const s = stripCommentForIndent(rawLine);
      if (s === 'if' || s.startsWith('if ')) stack.push('if');
      else if (s === 'while' || s.startsWith('while ')) stack.push('while');
      else if (s === 'endif') {
        const idx = stack.lastIndexOf('if');
        if (idx >= 0) stack.splice(idx, 1);
      } else if (s === 'endwhile') {
        const idx = stack.lastIndexOf('while');
        if (idx >= 0) stack.splice(idx, 1);
      }
    }
    return {
      openIf: stack.includes('if'),
      openWhile: stack.includes('while'),
      lastOpened: stack[stack.length - 1] || null,
    };
  }

  function computeAutoComplete(el, isRepl = false) {
    const start = el.selectionStart ?? el.value.length;
    const end = el.selectionEnd ?? el.value.length;
    if (start !== end) return [];
    const text = el.value;
    const lineStart = text.lastIndexOf('\n', start - 1) + 1;
    const lineBeforeCursor = text.slice(lineStart, start);
    if (isInsideCommentOrString(lineBeforeCursor)) return [];

    const tokenStart = findTokenStart(lineBeforeCursor);
    const prefix = lineBeforeCursor.slice(tokenStart);
    const prefixBeforeToken = lineBeforeCursor.slice(0, tokenStart).trim();
    const vars = extractDeclaredVars(isRepl ? `${codeEditor.value}\n${text}` : text);
    const blocks = analyzeOpenBlocks(text.slice(0, lineStart));

    if (!prefix) {
      const lastWord = prefixBeforeToken.split(/\s+/).pop() || '';
      if (AC_ARG_TRIGGERS.has(lastWord) || prefixBeforeToken.endsWith('=')) {
        const out = vars.map((v) => ({
          id: `var_${v}`, label: v, insertText: v, kind: 'VAR', detail: 'var', cursorBack: 0,
        }));
        if (lastWord === 'if' || lastWord === 'while' || prefixBeforeToken.endsWith('=')) {
          out.push(AC_CATALOG.find((c) => c.id === 'bool_true'));
          out.push(AC_CATALOG.find((c) => c.id === 'bool_false'));
          if (lastWord === 'if' || lastWord === 'while') {
            out.push(AC_CATALOG.find((c) => c.id === 'op_not'));
          }
        }
        return out.slice(0, 10);
      }
      return [];
    }

    const pool = (isRepl ? AC_CATALOG.filter((c) => c.kind !== 'BLOCO') : AC_CATALOG).concat(
      vars.map((v) => ({ id: `var_${v}`, label: v, insertText: v, kind: 'VAR', detail: 'var', cursorBack: 0 }))
    );
    const lowerPrefix = prefix.toLowerCase();
    const atStart = prefixBeforeToken.length === 0;
    const scored = [];

    for (const item of pool) {
      const lowerLabel = item.label.toLowerCase();
      let base = 0;
      if (item.label.startsWith(prefix) && item.label !== prefix) base = 120;
      else if (lowerLabel.startsWith(lowerPrefix) && lowerLabel !== lowerPrefix) base = 105;
      else if (item.label === prefix && item.kind === 'BLOCO') base = 95;
      else if (lowerLabel.includes('.' + lowerPrefix)) base = 80;
      else if (lowerLabel.includes(lowerPrefix) && prefix.length >= 2) base = 55;
      if (!base) continue;

      let bonus = 0;
      if (atStart && ['CMD', 'KW', 'TIPO', 'BLOCO'].includes(item.kind)) bonus += 15;
      if (!atStart && item.kind === 'VAR') bonus += 25;
      if (blocks.lastOpened === 'if' && (item.label === 'endif' || item.label === 'else')) bonus += 35;
      else if (blocks.lastOpened === 'while' && item.label === 'endwhile') bonus += 35;
      scored.push({ item, score: base + bonus });
    }

    scored.sort((a, b) => b.score - a.score || a.item.label.length - b.item.label.length);
    const seen = new Set();
    const res = [];
    for (const { item } of scored) {
      if (seen.has(item.label)) continue;
      seen.add(item.label);
      res.push(item);
      if (res.length >= 10) break;
    }
    return res;
  }

  function applyAutoComplete(el, item) {
    const start = el.selectionStart ?? el.value.length;
    const text = el.value;
    const lineStart = text.lastIndexOf('\n', start - 1) + 1;
    const lineBeforeCursor = text.slice(lineStart, start);
    const tokenStart = findTokenStart(lineBeforeCursor);
    const replaceStart = lineStart + tokenStart;
    const indent = (lineBeforeCursor.match(/^[ \t]*/) || [''])[0];
    const formatted = item.insertText.includes('\n') && indent
      ? item.insertText.replace(/\n/g, '\n' + indent)
      : item.insertText;
    const extraIndent = item.insertText.includes('\n') && item.cursorBack > 0
      ? (item.insertText.slice(-item.cursorBack).match(/\n/g) || []).length * indent.length
      : 0;
    el.value = text.slice(0, replaceStart) + formatted + text.slice(start);
    const nextCursor = Math.max(replaceStart, replaceStart + formatted.length - item.cursorBack - extraIndent);
    el.setSelectionRange(nextCursor, nextCursor);
    el.focus();
  }

  function renderAutoCompleteBar(barEl, el, isRepl = false) {
    const items = computeAutoComplete(el, isRepl);
    barEl.innerHTML = '';
    barEl.classList.toggle('hidden', items.length === 0);
    for (const item of items) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'ac-chip';
      btn.dataset.kind = item.kind;
      const badge = document.createElement('span');
      badge.className = 'ac-badge';
      badge.textContent = item.kind;
      const label = document.createElement('span');
      label.textContent = item.label;
      btn.appendChild(badge);
      btn.appendChild(label);
      if (item.detail) {
        const det = document.createElement('span');
        det.className = 'ac-detail';
        det.textContent = item.detail;
        btn.appendChild(det);
      }
      btn.addEventListener('click', () => {
        applyAutoComplete(el, item);
        if (!isRepl) {
          prevEditorText = codeEditor.value;
          syncEditorVisuals();
        }
        renderAutoCompleteBar(barEl, el, isRepl);
      });
      barEl.appendChild(btn);
    }
  }

  async function runProgramAndSwitchToConsole() {
    if (state.isRunning) return;
    await saveCurrent(true);
    setTab('console');
    setRunning(true);
    appendLog(`▶ A executar ${state.activeFile}...`, 'system');
    worker.postMessage({ type: 'run_program', code: codeEditor.value });
  }

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
      renderAutoCompleteBar($('autocomplete-bar'), codeEditor, false);
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
    renderAutoCompleteBar($('autocomplete-bar'), codeEditor, false);
  });

  codeEditor.addEventListener('keyup', (e) => {
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(e.key)) {
      renderAutoCompleteBar($('autocomplete-bar'), codeEditor, false);
    }
  });
  codeEditor.addEventListener('click', () => {
    renderAutoCompleteBar($('autocomplete-bar'), codeEditor, false);
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
    renderAutoCompleteBar($('autocomplete-bar'), codeEditor, false);
  });

  $('btn-run-fab').addEventListener('click', runProgramAndSwitchToConsole);
  btnRun.addEventListener('click', runProgramAndSwitchToConsole);
  $('btn-back-editor').addEventListener('click', () => setTab('editor'));

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

  replInput.addEventListener('input', () => {
    renderAutoCompleteBar($('repl-autocomplete-bar'), replInput, true);
  });

  replForm.addEventListener('submit', (e) => {
    e.preventDefault();
    if (state.isRunning) return;
    const line = replInput.value.trim();
    if (!line) return;
    appendLog(`${replPrompt.textContent} ${line}`, 'prompt');
    replInput.value = '';
    renderAutoCompleteBar($('repl-autocomplete-bar'), replInput, true);
    setRunning(true);
    worker.postMessage({ type: 'run_repl', line });
  });

  replControls.addEventListener('click', (e) => {
    const btn = e.target.closest('.chip-btn');
    if (!btn || state.isRunning) return;
    replInput.value = btn.dataset.cmd;
    replForm.requestSubmit();
  });

  $('tab-editor').addEventListener('click', () => setTab('editor'));
  $('tab-console').addEventListener('click', () => setTab('console'));
  $('tab-repl').addEventListener('click', () => setTab('repl'));

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

  $('btn-new-top').addEventListener('click', openNewFileDialog);
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
  $('btn-new-file').addEventListener('click', openNewFileDialog);
  $('btn-cancel-new-file').addEventListener('click', () => newFileDialog.classList.add('hidden'));

  newFileForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const raw = newFileNameInput.value.trim();
    if (!raw) return;
    const rec = await window.BrainIDB.saveFile(raw, '');
    state.activeFile = rec.name;
    state.savedSnapshot = rec.content;
    codeEditor.value = '';
    $('active-filename').textContent = rec.name;
    localStorage.setItem('brain_last_file', rec.name);
    syncEditorVisuals();
    newFileDialog.classList.add('hidden');
    setTab('editor');
    requestAnimationFrame(() => codeEditor.focus());
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
    setTab('editor');
    fileModal.classList.add('hidden');
    e.target.value = '';
  });

  async function init() {
    applyThemeAndFont();
    spawnWorker();
    setTab('editor');
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
