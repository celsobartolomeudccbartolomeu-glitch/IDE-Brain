/**
 * Módulo de gestão de ficheiros offline para a Brain IDE PWA usando IndexedDB.
 * Suporta guardar, abrir, listar, apagar, importar e exportar ficheiros .brain.
 */
(function (global) {
  'use strict';

  const DB_NAME = 'BrainIdeDB';
  const DB_VERSION = 1;
  const STORE_NAME = 'brain_files';

  const DEFAULT_SCRIPTS = [
    {
      name: 'ola_brain.brain',
      content: [
        '# Bem-vindo ao Brain Language!',
        'Brain.data_types',
        'int idade = 20',
        'float pi = 3.14',
        'char letra = "B"',
        'bool ativo = true',
        '',
        'IA.digitalwrite.line "=== Brain IDE PWA ==="',
        'print letra',
        'print "Idade + 5:"',
        'soma idade 5',
        '',
        'if idade >= 18',
        '    print "Maior de idade"',
        'else',
        '    print "Menor de idade"',
        'endif',
        'show.vars',
      ].join('\n'),
    },
    {
      name: 'contador_while.brain',
      content: [
        '// Exemplo de ciclo while e condições aninhadas',
        'int n = 1',
        'while n <= 5',
        '    if n % 2 == 0',
        '        print "Par: " + n',
        '    else',
        '        print "Ímpar: " + n',
        '    endif',
        '    n = n + 1',
        'endwhile',
        'print "Fim da contagem!"',
      ].join('\n'),
    },
    {
      name: 'entrada_dados.brain',
      content: [
        '# Leitura interativa na consola',
        'IA.programa',
        'IA.digitalwrite.line "Calculadora Brain"',
        '.scanln.Br a',
        '.scanln.Br b',
        'print "Soma:"',
        'soma a b',
        'print "Divisão:"',
        'div a b',
        '.run',
      ].join('\n'),
    },
  ];

  let dbPromise = null;

  function normalizeBrainFileName(rawName) {
    const cleaned = String(rawName || '')
      .trim()
      .replace(/[/\\:*?"<>|]/g, '_') || 'sem_nome';
    if (cleaned.toLowerCase().endsWith('.brain')) {
      return cleaned.slice(0, -6) + '.brain';
    }
    return cleaned + '.brain';
  }

  function utf8ByteLength(text) {
    if (typeof TextEncoder !== 'undefined') {
      return new TextEncoder().encode(text).length;
    }
    return unescape(encodeURIComponent(text)).length;
  }

  function openDB() {
    if (dbPromise) return dbPromise;

    dbPromise = new Promise((resolve, reject) => {
      if (!global.indexedDB) {
        reject(new Error('IndexedDB não suportado neste navegador.'));
        return;
      }

      const request = global.indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'name' });
          store.createIndex('by_updatedAt', 'updatedAt', { unique: false });
        }
      };

      request.onsuccess = () => {
        const db = request.result;
        db.onversionchange = () => {
          db.close();
          dbPromise = null;
        };
        resolve(db);
      };

      request.onerror = () => {
        dbPromise = null;
        reject(request.error || new Error('Falha ao abrir IndexedDB.'));
      };
    });

    return dbPromise;
  }

  async function saveFile(rawName, content) {
    const db = await openDB();
    const name = normalizeBrainFileName(rawName);
    const text = String(content ?? '');
    const record = {
      name,
      content: text,
      size: utf8ByteLength(text),
      updatedAt: Date.now(),
    };

    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.put(record);
      req.onerror = () => reject(req.error || new Error('Erro ao guardar ficheiro.'));
      tx.oncomplete = () => resolve(record);
      tx.onerror = () => reject(tx.error || new Error('Erro na transação de escrita.'));
    });
  }

  async function openFile(rawName) {
    const db = await openDB();
    const name = normalizeBrainFileName(rawName);

    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.get(name);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => reject(req.error || new Error('Erro ao abrir ficheiro.'));
    });
  }

  async function listFiles() {
    const db = await openDB();

    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.getAll();
      req.onsuccess = () => {
        const items = (req.result || [])
          .filter((item) => item && typeof item.name === 'string' && item.name.endsWith('.brain'))
          .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
        resolve(items);
      };
      req.onerror = () => reject(req.error || new Error('Erro ao listar ficheiros.'));
    });
  }

  async function deleteFile(rawName) {
    const db = await openDB();
    const name = normalizeBrainFileName(rawName);

    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.delete(name);
      req.onerror = () => reject(req.error || new Error('Erro ao apagar ficheiro.'));
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => reject(tx.error || new Error('Erro na transação de remoção.'));
    });
  }

  async function seedDefaultsIfEmpty() {
    const existing = await listFiles();
    if (existing.length > 0) return existing;

    for (const script of DEFAULT_SCRIPTS) {
      await saveFile(script.name, script.content);
    }
    return listFiles();
  }

  function exportFile(rawName, content) {
    const name = normalizeBrainFileName(rawName);
    const text = String(content ?? '');
    if (global.AndroidBridge && typeof global.AndroidBridge.exportBrainFile === 'function') {
      global.AndroidBridge.exportBrainFile(name, text);
      return;
    }
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function importFile(fileObj) {
    if (!fileObj) throw new Error('Nenhum ficheiro selecionado.');
    const content = await fileObj.text();
    const name = normalizeBrainFileName(fileObj.name || 'importado.brain');
    return saveFile(name, content);
  }

  global.BrainIDB = {
    DB_NAME,
    STORE_NAME,
    normalizeBrainFileName,
    openDB,
    saveFile,
    openFile,
    listFiles,
    deleteFile,
    seedDefaultsIfEmpty,
    exportFile,
    importFile,
  };
})(typeof window !== 'undefined' ? window : globalThis);
