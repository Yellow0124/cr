require('dotenv').config();

const { createApp } = require('./app');

const PORT = Number(process.env.PORT || 4000);
const app = createApp();

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Ticket API running on http://0.0.0.0:${PORT}`);
  console.log(`Accessible from network at http://172.20.10.3:${PORT}`);
});
