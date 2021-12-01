//1) npm i ws
//2) node SignalServerExample
const WebSocket = require('ws');
const wss = new WebSocket.Server({ host:"", port: 3000 });

wss.on('connection', function connection(ws) {
  ws.on('message', function message(data, isBinary) {
		if (isBinary){
			console.log('isBinary');
			wss.clients.forEach(function each(client) {
			  if (client !== ws && client.readyState === WebSocket.OPEN) {
				client.send(data, { binary: true });
			  }
			});
		}else{
			//todo add check token
			console.log('received: %s', data);
			console.log('isNotBinary!');
		}
	});
  
  ws.on('close', function close() {
		console.log('disconnected');
	});
});