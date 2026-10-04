// Test-only server. These labelled responses do not perform speech recognition.
const http = require('node:http');
const { Server } = require('socket.io');
const server = http.createServer((req, res) => { res.end('speech test fixture'); });
const io = new Server(server);
io.use((socket, next) => next(socket.handshake.auth.Authorization === 'fixture-only' ? undefined : new Error('invalid fixture key')));
io.on('connection', socket => {
  let microphone = false;
  let started = false;
  let answered = false;
  socket.on('start', (tasks, streaming) => {
    const task = tasks?.[0];
    if (task?.taskType !== 'asr' || task.config.samplingRate !== 8000 || streaming.responseTaskSequenceDepth !== 1) {
      socket.emit('abort'); return;
    }
    microphone = task.config.serviceId === 'fixture_microphone';
    started = true;
    console.log('fixture session ready: ' + task.config.serviceId);
    socket.emit('ready');
  });
  socket.on('data', (body, options, endOfStream, endOfSession) => {
    const bytes = body?.audio?.[0]?.audioContent;
    if (!started || !Buffer.isBuffer(bytes) || bytes.length !== 1600 || endOfStream !== false || endOfSession !== false) {
      socket.emit('abort'); return;
    }
    if (answered) return;
    answered = true;
    const label = microphone ? 'MICROPHONE TEST FIXTURE' : bytes.readInt16LE(0) === 1111 ? 'LOCAL TEST FIXTURE' : 'REMOTE TEST FIXTURE';
    console.log('received binary PCM chunk, bytes=' + bytes.length + ', response=' + label);
    const payload = text => ({ pipelineResponse: [{ taskType: 'asr', output: [{ source: text }] }] });
    socket.emit('response', payload(label + ' partial'), false);
    setTimeout(() => socket.connected && socket.emit('response', payload(label + ' completed'), true), 80);
  });
  socket.on('stop', () => socket.disconnect(true));
});
server.listen(3901, '0.0.0.0', () => console.log('Test speech fixture ready on port 3901'));
