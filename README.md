# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software** 

**Autor: David Santiago Palacios Pinzón**

Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
- **Identifica** y documenta en **`el reporte de laboratorio`**:
  - Posibles **condiciones de carrera**.
  - **Colecciones** o estructuras **no seguras** en contexto concurrente.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.

**Link repositorio parte 1: [Repositorio](https://github.com/Dav10-32/wait-notify-excercise?classId=d571652c-4593-4217-a1d5-18d74de3a645&assignmentId=5ea1c349-19df-4707-b9c2-0186795f77b2&submissionId=81672244-1198-b543-982d-50938324fe7c)**
## Parte II — Análisis de concurrencia (SnakeRace)

### Uso de hilos y autonomía de las serpientes

El sistema utiliza concurrencia asignando **un hilo independiente por cada serpiente**, implementado mediante la clase `SnakeRunner`, la cual implementa `Runnable`. Cada instancia de `SnakeRunner` se ejecuta en su propio hilo y controla de forma autónoma el movimiento, los giros aleatorios y la velocidad de su respectiva serpiente. Esto permite que múltiples serpientes se muevan simultáneamente sobre el mismo tablero sin depender de un ciclo centralizado.

El uso de un `Executor` con hilos virtuales permite escalar el número de serpientes manteniendo una ejecución eficiente y desacoplada.

---

### Posibles condiciones de carrera

Existen **potenciales condiciones de carrera** debido al acceso concurrente a objetos compartidos:

- **Objeto Board**: es compartido por todos los hilos `SnakeRunner`. Las operaciones que modifican su estado (`step`) están protegidas con `synchronized`, lo cual evita condiciones de carrera sobre las colecciones internas (ratones, obstáculos, turbo y teleports).
- **Objeto Snake**: cada serpiente es accedida por su hilo de ejecución y también por el hilo de la interfaz gráfica (para renderizar el estado). Métodos como `advance()` y `snapshot()` no están sincronizados, lo que podría generar inconsistencias temporales al leer el cuerpo de la serpiente mientras está siendo modificado.

---

### Colecciones o estructuras no seguras en contexto concurrente

Se identifican las siguientes estructuras no thread-safe:

- En `Board`, se utilizan `HashSet` y `HashMap` para almacenar ratones, obstáculos, turbo y teleports. Aunque estas colecciones no son seguras en concurrencia, su acceso está correctamente protegido mediante métodos `synchronized`, lo que evita problemas de concurrencia.
- En `Snake`, el cuerpo se almacena en un `ArrayDeque`, el cual no es thread-safe. Dado que este objeto es leído desde el hilo de la interfaz gráfica y modificado desde el hilo de ejecución de la serpiente, existe un posible riesgo de condiciones de carrera.
- La lista `snakes` en `SnakeApp` es un `ArrayList` no sincronizado, pero su contenido no se modifica después de la inicialización, por lo que su uso concurrente es seguro en este contexto.

---

### Espera activa (busy-wait) y sincronización innecesaria

No se observa **busy-waiting** en el sistema. Cada hilo de serpiente controla su ritmo de ejecución mediante `Thread.sleep()`, lo que evita ciclos de espera activa y reduce el consumo innecesario de CPU.

La sincronización utilizada en `Board` es necesaria y está correctamente localizada en las secciones críticas que modifican el estado compartido. No se identifican bloques sincronizados redundantes o innecesarios en el código analizado.

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

## Correcciones mínimas y regiones críticas

Se corrigió la ejecución concurrente de las serpientes eliminando la falta de coordinación durante la pausa del juego. Anteriormente, la pausa solo afectaba el renderizado, mientras que los hilos de cada serpiente continuaban ejecutándose. Esto se resolvió mediante un estado de pausa compartido y el uso de `wait()` / `notifyAll()`, evitando ejecución innecesaria y consumo de CPU.

Las regiones críticas se limitaron al acceso a los recursos compartidos del tablero. El método `step()` sincroniza únicamente las estructuras que representan el estado global del juego (ratones, obstáculos, turbo y teletransportes), evitando bloqueos innecesarios sobre la lógica de movimiento.

Con estos cambios se garantiza una pausa real del sistema, correcta sincronización entre hilos y un uso eficiente de los mecanismos de concurrencia de Java.

### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.

### Explicación de lo solicitado

La UI implementa los estados Iniciar, Pausar y Reanudar usando el botón Action y el GameClock.
Al solicitar la pausa, esta no se asume inmediata: primero se envía la señal de pausa a todos
los SnakeRunner y luego se espera a que cada hilo confirme que está efectivamente detenido.

Solo cuando todos los hilos se encuentran en pausa se toma un snapshot del estado del juego,
evitando lecturas parciales o inconsistentes (tearing). En este punto se calcula la serpiente
viva más larga y la peor serpiente, garantizando que la información mostrada corresponde a un
estado coherente del sistema.


### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

---

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**
