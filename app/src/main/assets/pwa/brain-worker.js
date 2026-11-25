'use strict';

const VERSION = 'Brain Language v1.10_test-fix3';
const MAX_PROGRAM_WHILE = 1000;
const MAX_REPL_WHILE = 50;

let variaveis = Object.create(null);
let modoPrograma = false;
let programa = [];
let pendingInputResolve = null;

function emit(text, level = 'output') {
  self.postMessage({ type: 'output', text: String(text), level });
}

function requestInput(varName) {
  return new Promise((resolve) => {
    pendingInputResolve = resolve;
    self.postMessage({ type: 'input_request', varName });
  });
}

function normalizaResultado(r) {
  if (typeof r === 'number' && Number.isFinite(r) && Number.isInteger(r)) {
    return r;
  }
  return r;
}

function paraNumero(v) {
  if (typeof v === 'boolean' || v === null || v === undefined) return null;
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  const s = String(v).trim();
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function isTruthy(v) {
  if (v === null || v === undefined) return false;
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v !== 0;
  if (typeof v === 'string') return v.length > 0;
  return Boolean(v);
}

function formatValue(v) {
  if (v === null || v === undefined) return '';
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  return String(normalizaResultado(v));
}

function isSingleQuotedLiteral(text) {
  if (text.length < 2) return false;
  const q = text[0];
  if ((q !== '"' && q !== "'") || text[text.length - 1] !== q) return false;
  for (let i = 1; i < text.length - 1; i++) {
    if (text[i] === '\\') {
      i++;
      continue;
    }
    if (text[i] === q) return false;
  }
  return true;
}

function unquote(text) {
  let out = '';
  for (let i = 1; i < text.length - 1; i++) {
    if (text[i] === '\\' && i + 1 < text.length - 1) {
      const next = text[i + 1];
      if (next === 'n') out += '\n';
      else if (next === 't') out += '\t';
      else if (next === 'r') out += '\r';
      else out += next;
      i++;
    } else {
      out += text[i];
    }
  }
  return out;
}

function stripInlineComment(linhaBruta) {
  const s = String(linhaBruta ?? '');
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

// Parser próprio e seguro (NUNCA usa eval)
function tokenize(input) {
  const tokens = [];
  let i = 0;
  const n = input.length;

  while (i < n) {
    const c = input[i];
    if (/\s/.test(c)) {
      i++;
      continue;
    }
    if (c === '#' || (c === '/' && i + 1 < n && input[i + 1] === '/')) {
      break;
    }
    if (c === '(') {
      tokens.push({ kind: 'lparen' });
      i++;
      continue;
    }
    if (c === ')') {
      tokens.push({ kind: 'rparen' });
      i++;
      continue;
    }
    if (c === '"' || c === "'") {
      const q = c;
      i++;
      let s = '';
      let closed = false;
      while (i < n) {
        if (input[i] === '\\' && i + 1 < n) {
          const nxt = input[i + 1];
          s += nxt === 'n' ? '\n' : nxt === 't' ? '\t' : nxt === 'r' ? '\r' : nxt;
          i += 2;
        } else if (input[i] === q) {
          closed = true;
          i++;
          break;
        } else {
          s += input[i];
          i++;
        }
      }
      if (!closed) throw new Error('String não fechada');
      tokens.push({ kind: 'str', value: s });
      continue;
    }
    if (/[0-9]/.test(c) || (c === '.' && i + 1 < n && /[0-9]/.test(input[i + 1]))) {
      let start = i;
      let hasDot = false;
      while (i < n) {
        if (/[0-9]/.test(input[i])) i++;
        else if (input[i] === '.' && !hasDot) {
          hasDot = true;
          i++;
        } else break;
      }
      const num = Number(input.slice(start, i));
      if (!Number.isFinite(num)) throw new Error('Número inválido');
      tokens.push({ kind: 'num', value: num });
      continue;
    }
    const two = input.slice(i, i + 2);
    if (['==', '!=', '<=', '>=', '&&', '||', '**'].includes(two)) {
      tokens.push({ kind: 'op', value: two });
      i += 2;
      continue;
    }
    if ('+-*/%^<>!'.includes(c)) {
      tokens.push({ kind: 'op', value: c });
      i++;
      continue;
    }
    if (/[A-Za-z_]/.test(c)) {
      const start = i;
      while (i < n && /[A-Za-z0-9_]/.test(input[i])) i++;
      const ident = input.slice(start, i);
      if (ident === 'true' || ident === 'True') tokens.push({ kind: 'bool', value: true });
      else if (ident === 'false' || ident === 'False') tokens.push({ kind: 'bool', value: false });
      else if (ident === 'and' || ident === 'or' || ident === 'not') {
        tokens.push({ kind: 'op', value: ident });
      } else tokens.push({ kind: 'ident', value: ident });
      continue;
    }
    throw new Error('Caractere inválido na expressão');
  }
  return tokens;
}

function evalSeguro(expr) {
  const tokens = tokenize(expr);
  if (tokens.length === 0) throw new Error('Expressão vazia');
  let pos = 0;

  const peek = () => tokens[pos];
  const matchOp = (...ops) => {
    const t = peek();
    if (t && t.kind === 'op' && ops.includes(t.value)) {
      pos++;
      return t.value;
    }
    return null;
  };
  const asNum = (v) => {
    if (typeof v === 'number') return v;
    if (typeof v === 'boolean') return v ? 1 : 0;
    throw new Error('Operando numérico esperado');
  };

  function parseOr() {
    let left = parseAnd();
    while (matchOp('or', '||')) {
      const right = parseAnd();
      left = isTruthy(left) || isTruthy(right);
    }
    return left;
  }

  function parseAnd() {
    let left = parseNot();
    while (matchOp('and', '&&')) {
      const right = parseNot();
      left = isTruthy(left) && isTruthy(right);
    }
    return left;
  }

  function parseNot() {
    if (matchOp('not', '!')) return !isTruthy(parseNot());
    return parseComparison();
  }

  function parseComparison() {
    const values = [parseAddSub()];
    const ops = [];
    while (true) {
      const op = matchOp('==', '!=', '<=', '>=', '<', '>');
      if (!op) break;
      ops.push(op);
      values.push(parseAddSub());
    }
    if (ops.length === 0) return values[0];
    let ok = true;
    for (let idx = 0; idx < ops.length; idx++) {
      const a = values[idx];
      const b = values[idx + 1];
      const op = ops[idx];
      if (op === '==') ok = ok && a == b;
      else if (op === '!=') ok = ok && a != b;
      else if (op === '<') ok = ok && a < b;
      else if (op === '<=') ok = ok && a <= b;
      else if (op === '>') ok = ok && a > b;
      else if (op === '>=') ok = ok && a >= b;
    }
    return ok;
  }

  function parseAddSub() {
    let left = parseMulDiv();
    while (true) {
      const op = matchOp('+', '-');
      if (!op) break;
      const right = parseMulDiv();
      if (op === '+') {
        left =
          typeof left === 'string' || typeof right === 'string'
            ? formatValue(left) + formatValue(right)
            : normalizaResultado(asNum(left) + asNum(right));
      } else {
        left = normalizaResultado(asNum(left) - asNum(right));
      }
    }
    return left;
  }

  function parseMulDiv() {
    let left = parseUnary();
    while (true) {
      const op = matchOp('*', '/', '//', '%');
      if (!op) break;
      const right = parseUnary();
      if (op === '*') {
        if (typeof left === 'string' && typeof right === 'number') {
          left = left.repeat(Math.max(0, Math.min(1000, Math.trunc(right))));
        } else {
          left = normalizaResultado(asNum(left) * asNum(right));
        }
      } else if (op === '/') {
        const b = asNum(right);
        if (b === 0) throw new Error('DIV_ZERO');
        left = normalizaResultado(asNum(left) / b);
      } else if (op === '//') {
        const b = asNum(right);
        if (b === 0) throw new Error('DIV_ZERO');
        left = Math.floor(asNum(left) / b);
      } else if (op === '%') {
        const b = asNum(right);
        if (b === 0) throw new Error('DIV_ZERO');
        left = normalizaResultado(asNum(left) % b);
      }
    }
    return left;
  }

  function parseUnary() {
    const op = matchOp('+', '-');
    if (op) {
      const val = asNum(parseUnary());
      return op === '-' ? normalizaResultado(-val) : val;
    }
    return parsePower();
  }

  function parsePower() {
    const base = parsePrimary();
    if (matchOp('**', '^')) {
      const exp = parseUnary();
      return normalizaResultado(Math.pow(asNum(base), asNum(exp)));
    }
    return base;
  }

  function parsePrimary() {
    const tok = peek();
    if (!tok) throw new Error('Expressão incompleta');
    if (tok.kind === 'num' || tok.kind === 'str' || tok.kind === 'bool') {
      pos++;
      return tok.value;
    }
    if (tok.kind === 'ident') {
      pos++;
      if (!(tok.value in variaveis)) throw new Error('Variável não definida');
      return variaveis[tok.value];
    }
    if (tok.kind === 'lparen') {
      pos++;
      const inner = parseOr();
      const close = peek();
      if (!close || close.kind !== 'rparen') throw new Error('Parêntese esperado');
      pos++;
      return inner;
    }
    throw new Error('Expressão inválida');
  }

  const res = parseOr();
  if (pos < tokens.length) throw new Error('Token inesperado');
  return normalizaResultado(res);
}

function avaliarExpressao(expr) {
  try {
    return evalSeguro(expr);
  } catch {
    return expr;
  }
}

function tipoDado(tipo, nome, valor) {
  if (tipo === 'int') {
    if (/^-?\d+$/.test(valor.trim())) {
      variaveis[nome] = parseInt(valor.trim(), 10);
    } else {
      emit('Erro: Valor inválido para int', 'error');
    }
  } else if (tipo === 'float') {
    const f = Number(valor.trim());
    if (valor.trim() !== '' && Number.isFinite(f)) {
      variaveis[nome] = f;
    } else {
      emit('Erro: Valor inválido para float', 'error');
    }
  } else if (tipo === 'char') {
    if (String(valor).length === 1) {
      variaveis[nome] = String(valor);
    } else {
      emit('Erro: char aceita apenas 1 caractere', 'error');
    }
  } else if (tipo === 'bool') {
    if (['true', 'True', '1'].includes(valor)) variaveis[nome] = true;
    else if (['false', 'False', '0'].includes(valor)) variaveis[nome] = false;
    else emit('Erro: Valor inválido para bool', 'error');
  } else {
    emit('Erro: Tipo de dado desconhecido', 'error');
  }
}

function imprimirTexto(texto) {
  if (isSingleQuotedLiteral(texto) && texto.startsWith('"')) {
    emit(unquote(texto));
  } else if (texto in variaveis) {
    emit(formatValue(variaveis[texto]));
  } else {
    try {
      emit(formatValue(evalSeguro(texto)));
    } catch (err) {
      if (err && err.message === 'DIV_ZERO') emit('Erro: Divisão por zero', 'error');
      else emit(texto);
    }
  }
}

async function executarLinha(linhaBruta) {
  const linha = stripInlineComment(linhaBruta);
  if (!linha) return;

  // FIX 1: '=' seguido de '=' não é atribuição
  const assignMatch = linha.match(/^(\w+)\s*=(?!=)\s*(.+)$/);
  if (assignMatch) {
    const nome = assignMatch[1];
    const valor = assignMatch[2].trim();
    if (isSingleQuotedLiteral(valor) && valor.startsWith('"')) {
      variaveis[nome] = unquote(valor);
    } else {
      variaveis[nome] = avaliarExpressao(valor);
    }
    return;
  }

  if (linha.startsWith('Brain.data_types')) {
    emit('Biblioteca Brain.data_types carregada', 'system');
    return;
  }

  if (
    linha.startsWith('int ') ||
    linha.startsWith('float ') ||
    linha.startsWith('char ') ||
    linha.startsWith('bool ')
  ) {
    const partes = linha.split(/\s+/);
    if (partes.length >= 4 && partes[2] === '=') {
      const tipo = partes[0];
      const nome = partes[1];
      let valor = partes.slice(3).join(' ');
      if (valor.startsWith('"') && valor.endsWith('"') && valor.length >= 2) {
        valor = valor.slice(1, -1);
      } else {
        valor = valor.replace(/^"+|"+$/g, '');
      }
      tipoDado(tipo, nome, valor);
    } else {
      emit('Erro: Formato incorreto para declaração de variável. Use: int nome = valor', 'error');
    }
    return;
  }

  if (
    linha.startsWith('soma ') ||
    linha.startsWith('sub ') ||
    linha.startsWith('multiplica ') ||
    linha.startsWith('div ')
  ) {
    const partes = linha.split(/\s+/);
    if (partes.length === 3) {
      const op = partes[0];
      const a = paraNumero(avaliarExpressao(partes[1]));
      const b = paraNumero(avaliarExpressao(partes[2]));
      if (a === null || b === null) {
        emit('Erro: Valores inválidos para operação', 'error');
        return;
      }
      if (op === 'soma') emit(formatValue(normalizaResultado(a + b)));
      else if (op === 'sub') emit(formatValue(normalizaResultado(a - b)));
      else if (op === 'multiplica') emit(formatValue(normalizaResultado(a * b)));
      else if (op === 'div') {
        if (b !== 0) emit(formatValue(normalizaResultado(a / b)));
        else emit('Erro: Divisão por zero', 'error');
      }
    } else {
      emit('Erro: Formato incorreto para operação. Use: soma a b', 'error');
    }
    return;
  }

  const binMatch = linha.match(/^(\w+|\d+(?:\.\d+)?)\s*([+\-*/])\s*(\w+|\d+(?:\.\d+)?)$/);
  if (binMatch) {
    const a = paraNumero(avaliarExpressao(binMatch[1].trim()));
    const op = binMatch[2];
    const b = paraNumero(avaliarExpressao(binMatch[3].trim()));
    if (a === null || b === null) {
      emit('Erro: Valores inválidos para operação', 'error');
      return;
    }
    if (op === '+') emit(formatValue(normalizaResultado(a + b)));
    else if (op === '-') emit(formatValue(normalizaResultado(a - b)));
    else if (op === '*') emit(formatValue(normalizaResultado(a * b)));
    else if (op === '/') {
      if (b !== 0) emit(formatValue(normalizaResultado(a / b)));
      else emit('Erro: Divisão por zero', 'error');
    }
    return;
  }

  if (linha === 'if' || linha.startsWith('if ')) {
    const condicao = linha.slice(2).trim();
    try {
      emit(isTruthy(evalSeguro(condicao)) ? 'Condição verdadeira' : 'Condição falsa');
    } catch {
      emit('Erro: Condição inválida', 'error');
    }
    return;
  }

  if (linha === 'else' || linha === 'else:') {
    emit('Bloco else executado (use dentro de IA.programa com endif)', 'warning');
    return;
  }

  if (linha === 'while' || linha.startsWith('while ')) {
    const condicao = linha.slice(5).trim();
    let contador = 0;
    while (true) {
      try {
        if (isTruthy(evalSeguro(condicao))) emit('Loop ativo');
        else break;
      } catch {
        emit('Erro: Condição inválida no while', 'error');
        break;
      }
      contador++;
      if (contador > MAX_REPL_WHILE) {
        emit('Loop interrompido: limite de 50 iterações', 'warning');
        break;
      }
    }
    return;
  }

  if (linha === 'IA.programa') {
    modoPrograma = true;
    programa = [];
    self.postMessage({ type: 'program_mode', active: true, count: 0 });
    emit("Modo programa iniciado. Digite '.run'", 'system');
    return;
  }

  if (linha === '.sair_prog') {
    modoPrograma = false;
    programa = [];
    self.postMessage({ type: 'program_mode', active: false, count: 0 });
    emit('Modo programa cancelado.', 'system');
    return;
  }

  if (linha === '.run') {
    emit('Executando programa...', 'system');
    const linhas = programa.slice();
    modoPrograma = false;
    programa = [];
    self.postMessage({ type: 'program_mode', active: false, count: 0 });
    await executarPrograma(linhas);
    return;
  }

  if (linha === 'see.lines') {
    if (programa.length === 0) emit('(programa vazio)', 'system');
    else programa.forEach((l, i) => emit(`${i + 1}: ${l}`));
    return;
  }

  if (linha.startsWith('.del ')) {
    const parts = linha.split(/\s+/);
    const num = Number.parseInt(parts[1], 10);
    if (!Number.isInteger(num)) emit('Erro: Use .del <numero>', 'error');
    else if (num >= 1 && num <= programa.length) {
      const removida = programa.splice(num - 1, 1)[0];
      self.postMessage({ type: 'program_mode', active: modoPrograma, count: programa.length });
      emit(`Linha ${num} removida: ${removida}`, 'system');
    } else emit('Erro: Número da linha inválido', 'error');
    return;
  }

  if (linha === '.version') {
    emit(VERSION, 'system');
    return;
  }

  if (linha === '.help') {
    [
      'Comandos disponíveis:',
      'IA.programa - Inicia o modo programa',
      '.run - Executa o programa',
      'see.lines - Mostra as linhas do programa',
      '.del <numero> - Remove uma linha do programa',
      '.sair_prog - Cancela o modo programa',
      '.version - Mostra a versão',
      '.help - Mostra ajuda',
      'IA.digitalwrite.line <texto> - Mostra um texto',
      '.screnclear - Limpa a tela',
      '.scanln.Br <variavel> - Lê entrada do usuário',
      'show.vars - Mostra variáveis',
      '.clear_var <variavel> - Apaga uma variável',
      'Brain.data_types - Carrega tipos de dados',
      'int/float/char/bool nome = valor - Declara variável',
      'soma/sub/multiplica/div a b - Operações',
      'if condicao / else / endif - Condição com bloco',
      'while condicao / endwhile - Loop com bloco',
      'print <texto> - Mostra um texto',
    ].forEach((line) => emit(line, 'system'));
    return;
  }

  if (linha.startsWith('IA.digitalwrite.line ')) {
    imprimirTexto(linha.slice('IA.digitalwrite.line '.length).trim());
    return;
  }

  if (linha === '.screnclear') {
    self.postMessage({ type: 'clear_screen' });
    return;
  }

  if (linha.startsWith('.scanln.Br ')) {
    const nome = linha.slice('.scanln.Br '.length).trim();
    if (!nome) {
      emit('Erro: Indique o nome da variável', 'error');
      return;
    }
    const valor = await requestInput(nome);
    if (valor.includes('.') && valor.trim() !== '' && Number.isFinite(Number(valor.trim()))) {
      variaveis[nome] = Number(valor.trim());
    } else if (/^-?\d+$/.test(valor.trim())) {
      variaveis[nome] = parseInt(valor.trim(), 10);
    } else {
      variaveis[nome] = valor;
    }
    return;
  }

  if (linha === 'show.vars') {
    const keys = Object.keys(variaveis);
    if (keys.length === 0) emit('Nenhuma variável definida', 'system');
    else keys.forEach((k) => emit(`${k} = ${formatValue(variaveis[k])}`));
    return;
  }

  if (linha.startsWith('.clear_var ')) {
    const nome = linha.slice('.clear_var '.length).trim();
    if (nome in variaveis) {
      delete variaveis[nome];
      emit(`Variável ${nome} removida`, 'system');
    } else {
      emit(`Variável ${nome} não encontrada`, 'warning');
    }
    return;
  }

  if (linha.startsWith('print ')) {
    imprimirTexto(linha.slice(6).trim());
    return;
  }

  if (linha in variaveis) {
    emit(formatValue(variaveis[linha]));
    return;
  }

  emit(`Comando desconhecido: ${linha}`, 'error');
}

async function executarPrograma(linhas) {
  let i = 0;
  while (i < linhas.length) {
    const linha = stripInlineComment(linhas[i]);
    if (!linha) {
      i++;
      continue;
    }

    if (linha === 'if' || linha.startsWith('if ')) {
      const condicao = linha.slice(2).trim();
      let nivel = 0;
      let posElse = -1;
      let posEndif = -1;
      for (let j = i + 1; j < linhas.length; j++) {
        const l = stripInlineComment(linhas[j]);
        if (l === 'if' || l.startsWith('if ')) nivel++;
        else if (l === 'endif') {
          if (nivel === 0) {
            posEndif = j;
            break;
          }
          nivel--;
        } else if (l === 'else' && nivel === 0) {
          if (posElse === -1) posElse = j;
          else emit("Aviso: 'else' duplicado ignorado", 'warning');
        }
      }
      if (posEndif === -1) {
        emit('Erro: if sem endif', 'error');
        i++;
        continue;
      }
      let res;
      try {
        res = evalSeguro(condicao);
      } catch {
        emit('Erro na condição do if', 'error');
        i = posEndif + 1;
        continue;
      }
      if (isTruthy(res)) {
        const fim = posElse !== -1 ? posElse : posEndif;
        await executarPrograma(linhas.slice(i + 1, fim));
      } else if (posElse !== -1) {
        await executarPrograma(linhas.slice(posElse + 1, posEndif));
      }
      i = posEndif + 1;
      continue;
    }

    if (linha === 'while' || linha.startsWith('while ')) {
      const condicao = linha.slice(5).trim();
      let nivel = 0;
      let posEnd = -1;
      for (let j = i + 1; j < linhas.length; j++) {
        const l = stripInlineComment(linhas[j]);
        if (l === 'while' || l.startsWith('while ')) nivel++;
        else if (l === 'endwhile') {
          if (nivel === 0) {
            posEnd = j;
            break;
          }
          nivel--;
        }
      }
      if (posEnd === -1) {
        emit('Erro: while sem endwhile', 'error');
        i++;
        continue;
      }
      let contador = 0;
      const corpo = linhas.slice(i + 1, posEnd);
      while (true) {
        let res;
        try {
          res = evalSeguro(condicao);
        } catch {
          emit('Erro no while', 'error');
          break;
        }
        if (!isTruthy(res)) break;
        await executarPrograma(corpo);
        contador++;
        if (contador > MAX_PROGRAM_WHILE) {
          emit('Loop interrompido: limite de 1000 iterações', 'warning');
          break;
        }
      }
      i = posEnd + 1;
      continue;
    }

    if (linha === 'else' || linha === 'endif' || linha === 'endwhile') {
      i++;
      continue;
    }

    await executarLinha(linha);
    i++;
  }
}

self.onmessage = async (event) => {
  const msg = event.data;
  if (!msg) return;

  if (msg.type === 'input_response') {
    if (pendingInputResolve) {
      const resolve = pendingInputResolve;
      pendingInputResolve = null;
      resolve(String(msg.value ?? ''));
    }
    return;
  }

  if (msg.type === 'reset') {
    variaveis = Object.create(null);
    modoPrograma = false;
    programa = [];
    self.postMessage({ type: 'program_mode', active: false, count: 0 });
    return;
  }

  if (msg.type === 'run_program') {
    variaveis = Object.create(null);
    const rawLines = String(msg.code || '').split(/\r?\n/);
    const codeOnly = rawLines
      .map((l) => stripInlineComment(l))
      .filter((l) => l.length > 0);
    let linesToRun = rawLines;
    if (codeOnly[0] === 'IA.programa' && codeOnly[codeOnly.length - 1] === '.run') {
      const first = rawLines.findIndex((l) => stripInlineComment(l) === 'IA.programa');
      let last = -1;
      for (let idx = rawLines.length - 1; idx >= 0; idx--) {
        if (stripInlineComment(rawLines[idx]) === '.run') {
          last = idx;
          break;
        }
      }
      if (first >= 0 && last > first) linesToRun = rawLines.slice(first + 1, last);
    }
    await executarPrograma(linesToRun);
    self.postMessage({ type: 'done' });
    return;
  }

  if (msg.type === 'run_repl') {
    const entrada = stripInlineComment(msg.line || '');
    if (!entrada) {
      self.postMessage({ type: 'done' });
      return;
    }
    if (entrada.toLowerCase() === 'sair') {
      emit('Saindo do Brain Language. Até logo!', 'system');
      modoPrograma = false;
      programa = [];
      self.postMessage({ type: 'program_mode', active: false, count: 0 });
      self.postMessage({ type: 'done' });
      return;
    }
    if (modoPrograma) {
      if (
        ['.run', 'see.lines', '.sair_prog', '.help', '.version', '.screnclear'].includes(entrada) ||
        entrada.startsWith('.del ')
      ) {
        await executarLinha(entrada);
      } else {
        programa.push(entrada);
        self.postMessage({ type: 'program_mode', active: true, count: programa.length });
      }
    } else {
      await executarLinha(entrada);
    }
    self.postMessage({ type: 'done' });
  }
};
