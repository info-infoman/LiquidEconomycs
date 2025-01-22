//1) npm i ws
//2) node SignalServerExample
const WebSocket = require('ws');
const url = require('url');
const wss = new WebSocket.Server({ host:"", port: 3000 });

wss.on('connection', function connection(ws, req) {
	const parameters = url.parse(req.url, true);
    ws.chatRoom = parameters.query.chatRoom;
    console.log('new client in chat room #' + ws.chatRoom);
	ws.on('message', function message(data, isBinary) {
		if (isBinary){
			console.log('new msg in chat room #' + ws.chatRoom);
			wss.clients.forEach(function each(client) {
			  if (client !== ws && client.chatRoom == ws.chatRoom && client.readyState === WebSocket.OPEN) {
				client.send(data, { binary: true });
			  }
			});
		}
	});
  
	ws.on('close', function close() {
		console.log('disconnected');
	});
});