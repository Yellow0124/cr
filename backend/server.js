require('dotenv').config();

const { createApp } = require('./app');

const PORT = Number(process.env.PORT || 4000);
const app = createApp();

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Ticket API running on http://0.0.0.0:${PORT}`);
  console.log(`Accessible from network at http://192.168.0.138:${PORT}`);
});
