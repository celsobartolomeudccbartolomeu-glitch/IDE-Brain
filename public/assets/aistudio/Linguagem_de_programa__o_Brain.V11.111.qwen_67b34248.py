# Brain Language v1.10_test-fix3
# Desenvolvida por: Celso Bartolomeu e agente de IA
#
# Correções em relação ao test-fix2:
#   FIX 1 - 'x == 5' já não é interpretado como atribuição silenciosa
#   FIX 2 - char aceita apenas 1 caractere
#   FIX 3 - modo rápido: inicie com --fast (ou BRAIN_FAST=1) para pular animações
#   FIX 4 - operações preservam inteiros (soma 3 4 -> 7, não 7.0)
#   FIX 5 - eval() sem builtins (expressões Python arbitrárias bloqueadas)
#   FIX 6 - .help/.version/.screnclear funcionam dentro do modo programa

import time
import os
import re
import sys

MODO_RAPIDO = "--fast" in sys.argv or os.environ.get("BRAIN_FAST") == "1"

# --- Mensagem inicial: aparece e some depois de 5s ---
print("Brain Script Interpreter iniciado")
if not MODO_RAPIDO:
    time.sleep(5)
    os.system("cls" if os.name == "nt" else "clear")

variaveis = {}
modo_programa = False
programa = []

def mensagem_temporaria(texto, segundos=5):
    """Mostra a mensagem, espera 5s e apaga todas as linhas que ela ocupou."""
    print(texto)
    if MODO_RAPIDO:
        return
    time.sleep(segundos)
    try:
        largura = os.get_terminal_size().columns
    except:
        largura = 80
    n_linhas = (len(texto) + largura - 1) // largura
    if n_linhas < 1:
        n_linhas = 1
    for _ in range(n_linhas):
        print("\033[1A\033[2K", end="", flush=True)

def tipo_dado(tipo, nome, valor):
    if tipo == "int":
        try:
            variaveis[nome] = int(valor)
        except ValueError:
            print("Erro: Valor inválido para int")
    elif tipo == "float":
        try:
            variaveis[nome] = float(valor)
        except ValueError:
            print("Erro: Valor inválido para float")
    elif tipo == "char":
        # FIX 2: char aceita exatamente 1 caractere
        if len(str(valor)) == 1:
            variaveis[nome] = str(valor)
        else:
            print("Erro: char aceita apenas 1 caractere")
    elif tipo == "bool":
        if valor in ["true", "True", "1"]:
            variaveis[nome] = True
        elif valor in ["false", "False", "0"]:
            variaveis[nome] = False
        else:
            print("Erro: Valor inválido para bool")
    else:
        print("Erro: Tipo de dado desconhecido")

def soma(a, b):
    return a + b

def sub(a, b):
    return a - b

def multiplica(a, b):
    return a * b

def div(a, b):
    if b != 0:
        return normaliza_resultado(a / b)
    else:
        print("Erro: Divisão por zero")
        return None

def para_numero(v):
    """Converte para int quando possível, senão float; None se inválido."""
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float)):
        return v
    try:
        return int(str(v))
    except (ValueError, TypeError):
        try:
            return float(str(v))
        except (ValueError, TypeError):
            return None

def normaliza_resultado(r):
    """FIX 4: 7.0 vira 7 quando o resultado é inteiro."""
    if isinstance(r, float) and r.is_integer():
        return int(r)
    return r

def eval_seguro(expr):
    """FIX 5: eval sem builtins - bloqueia código Python arbitrário."""
    return eval(expr, {"__builtins__": {}}, variaveis)

def avaliar_expressao(expr):
    try:
        return eval_seguro(expr)
    except:
        return expr

def executar_linha(linha):
    global modo_programa, programa
    linha = linha.strip()

    if not linha or linha.startswith("#") or linha.startswith("//"):
        return

    # FIX 1: '=' seguido de outro '=' não é atribuição (ex.: x == 5)
    m = re.match(r"^(\w+)\s*=(?!=)\s*(.+)$", linha)
    if m:
        nome = m.group(1)
        valor = m.group(2).strip()
        if valor.startswith('"') and valor.endswith('"'):
            variaveis[nome] = valor[1:-1]
        else:
            variaveis[nome] = avaliar_expressao(valor)
        return

    if linha.startswith("Brain.data_types"):
        print("Biblioteca Brain.data_types carregada")
        return

    if linha.startswith("int ") or linha.startswith("float ") or linha.startswith("char ") or linha.startswith("bool "):
        partes = linha.split()
        if len(partes) >= 4 and partes[2] == "=":
            tipo = partes[0]
            nome = partes[1]
            valor = " ".join(partes[3:])
            valor = valor.strip('"')
            tipo_dado(tipo, nome, valor)
        else:
            print("Erro: Formato incorreto para declaração de variável. Use: int nome = valor")
        return

    if linha.startswith("soma ") or linha.startswith("sub ") or linha.startswith("multiplica ") or linha.startswith("div "):
        partes = linha.split()
        if len(partes) == 3:
            op = partes[0]
            a = para_numero(avaliar_expressao(partes[1]))
            b = para_numero(avaliar_expressao(partes[2]))
            if a is None or b is None:
                print("Erro: Valores inválidos para operação")
                return
            if op == "soma":
                print(normaliza_resultado(soma(a, b)))
            elif op == "sub":
                print(normaliza_resultado(sub(a, b)))
            elif op == "multiplica":
                print(normaliza_resultado(multiplica(a, b)))
            elif op == "div":
                resultado = div(a, b)
                if resultado is not None:
                    print(resultado)
        else:
            print("Erro: Formato incorreto para operação. Use: soma a b")
        return

    if re.match(r"^\w+\s*[\+\-\*\/]\s*\w+$", linha) or re.match(r"^\d+\s*[\+\-\*\/]\s*\d+$", linha):
        partes = re.split(r"(\+|\-|\*|/)", linha)
        if len(partes) == 3:
            a = para_numero(avaliar_expressao(partes[0].strip()))
            op = partes[1]
            b = para_numero(avaliar_expressao(partes[2].strip()))
            if a is None or b is None:
                print("Erro: Valores inválidos para operação")
                return
            if op == "+":
                print(normaliza_resultado(a + b))
            elif op == "-":
                print(normaliza_resultado(a - b))
            elif op == "*":
                print(normaliza_resultado(a * b))
            elif op == "/":
                resultado = div(a, b)
                if resultado is not None:
                    print(resultado)
        return

    if linha == "if" or linha.startswith("if "):
        condicao = linha[2:].strip()
        try:
            resultado = eval_seguro(condicao)
            if resultado:
                print("Condição verdadeira")
            else:
                print("Condição falsa")
        except:
            print("Erro: Condição inválida")
        return

    if linha == "else" or linha == "else:":
        print("Bloco else executado (use dentro de IA.programa com endif)")
        return

    if linha == "while" or linha.startswith("while "):
        condicao = linha[5:].strip()
        contador = 0
        while True:
            try:
                resultado = eval_seguro(condicao)
                if resultado:
                    print("Loop ativo")
                else:
                    break
            except:
                print("Erro: Condição inválida no while")
                break
            contador += 1
            if contador > 50:
                print("Loop interrompido: limite de 50 iterações")
                break
        return

    if linha == "IA.programa":
        modo_programa = True
        programa.clear()
        mensagem_temporaria("Modo programa iniciado. Digite '.run'")
        return

    if linha == ".sair_prog":
        modo_programa = False
        programa.clear()
        print("Modo programa cancelado.")
        return

    if linha == ".run":
        mensagem_temporaria("Executando programa...")
        executar_programa(programa)
        modo_programa = False
        programa.clear()
        return

    if linha == "see.lines":
        for i, l in enumerate(programa, 1):
            print(f"{i}: {l}")
        return

    if linha.startswith(".del "):
        try:
            num = int(linha.split()[1])
            if 1 <= num <= len(programa):
                removida = programa.pop(num - 1)
                print(f"Linha {num} removida: {removida}")
            else:
                print("Erro: Número da linha inválido")
        except:
            print("Erro: Use .del <numero>")
        return

    if linha == ".version":
        print("Brain Language v1.10_test-fix3")
        return

    if linha == ".help":
        print("Comandos disponíveis:")
        print("IA.programa - Inicia o modo programa")
        print(".run - Executa o programa")
        print("see.lines - Mostra as linhas do programa")
        print(".del <numero> - Remove uma linha do programa")
        print(".sair_prog - Cancela o modo programa")
        print(".version - Mostra a versão")
        print(".help - Mostra ajuda")
        print("IA.digitalwrite.line <texto> - Mostra um texto")
        print(".screnclear - Limpa a tela")
        print(".scanln.Br <variavel> - Lê entrada do usuário")
        print("show.vars - Mostra variáveis")
        print(".clear_var <variavel> - Apaga uma variável")
        print("Brain.data_types - Carrega tipos de dados")
        print("int/float/char/bool nome = valor - Declara variável")
        print("soma/sub/multiplica/div a b - Operações")
        print("if condicao / else / endif - Condição com bloco")
        print("while condicao / endwhile - Loop com bloco")
        print("print <texto> - Mostra um texto")
        print("--fast (ao iniciar) - Pula animações e esperas")
        return

    if linha.startswith("IA.digitalwrite.line "):
        texto = linha[len("IA.digitalwrite.line "):].strip()
        if texto.startswith('"') and texto.endswith('"'):
            print(texto[1:-1])
        elif texto in variaveis:
            print(variaveis[texto])
        else:
            try:
                print(eval_seguro(texto))
            except:
                print(texto)
        return

    if linha == ".screnclear":
        os.system("cls" if os.name == "nt" else "clear")
        return

    if linha.startswith(".scanln.Br "):
        nome = linha[len(".scanln.Br "):].strip()
        valor = input(f"Digite {nome}: ")
        try:
            if "." in valor:
                variaveis[nome] = float(valor)
            else:
                variaveis[nome] = int(valor)
        except:
            variaveis[nome] = valor
        return

    if linha == "show.vars":
        if variaveis:
            for nome, valor in variaveis.items():
                print(f"{nome} = {valor}")
        else:
            print("Nenhuma variável definida")
        return

    if linha.startswith(".clear_var "):
        nome = linha[len(".clear_var "):].strip()
        if nome in variaveis:
            del variaveis[nome]
            print(f"Variável {nome} removida")
        else:
            print(f"Variável {nome} não encontrada")
        return

    if linha.startswith("print "):
        texto = linha[6:].strip()
        if texto.startswith('"') and texto.endswith('"'):
            print(texto[1:-1])
        elif texto in variaveis:
            print(variaveis[texto])
        else:
            try:
                print(eval_seguro(texto))
            except:
                print(texto)
        return

    if linha in variaveis:
        print(variaveis[linha])
        return

    print(f"Comando desconhecido: {linha}")

def executar_programa(programa):
    i = 0
    while i < len(programa):
        linha = programa[i].strip()

        if not linha or linha.startswith("#") or linha.startswith("//"):
            i += 1
            continue

        if linha == "if" or linha.startswith("if "):
            condicao = linha[2:].strip()
            nivel = 0
            pos_else = -1
            pos_endif = -1
            j = i + 1
            while j < len(programa):
                l = programa[j].strip()
                if l == "if" or l.startswith("if "):
                    nivel += 1
                elif l == "endif":
                    if nivel == 0:
                        pos_endif = j
                        break
                    nivel -= 1
                elif l == "else" and nivel == 0:
                    if pos_else == -1:
                        pos_else = j
                    else:
                        print("Aviso: 'else' duplicado ignorado")
                j += 1

            if pos_endif == -1:
                print("Erro: if sem endif")
                i += 1
                continue

            try:
                resultado = eval_seguro(condicao)
            except:
                print("Erro na condição do if")
                i = pos_endif + 1
                continue

            if resultado:
                fim = pos_else if pos_else != -1 else pos_endif
                executar_programa(programa[i+1:fim])
            elif pos_else != -1:
                executar_programa(programa[pos_else+1:pos_endif])

            i = pos_endif + 1
            continue

        if linha == "while" or linha.startswith("while "):
            condicao = linha[5:].strip()
            nivel = 0
            pos_end = -1
            j = i + 1
            while j < len(programa):
                l = programa[j].strip()
                if l == "while" or l.startswith("while "):
                    nivel += 1
                elif l == "endwhile":
                    if nivel == 0:
                        pos_end = j
                        break
                    nivel -= 1
                j += 1

            if pos_end == -1:
                print("Erro: while sem endwhile")
                i += 1
                continue

            contador = 0
            while True:
                try:
                    resultado = eval_seguro(condicao)
                except:
                    print("Erro no while")
                    break
                if not resultado:
                    break
                executar_programa(programa[i+1:pos_end])
                contador += 1
                if contador > 1000:
                    print("Loop interrompido: limite de 1000 iterações")
                    break

            i = pos_end + 1
            continue

        if linha in ("else", "endif", "endwhile"):
            i += 1
            continue

        executar_linha(linha)
        i += 1

print("Bem-vindo ao Brain Language v1.10_test-fix3")
mensagem_temporaria("Digite '.help' para ajuda")

while True:
    try:
        entrada = input(">>> ")
    except (EOFError, KeyboardInterrupt):
        print("\nSaindo...")
        break

    entrada = entrada.strip()
    if not entrada:
        continue
    if entrada.lower() == "sair":
        print("Saindo do Brain Language. Até logo!")
        break

    if modo_programa:
        # FIX 6: comandos de sistema também funcionam dentro do modo programa
        if entrada in (".run", "see.lines", ".sair_prog", ".help", ".version", ".screnclear"):
            executar_linha(entrada)
        elif entrada.startswith(".del "):
            executar_linha(entrada)
        else:
            programa.append(entrada)
    else:
        executar_linha(entrada)