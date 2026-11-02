import axios from 'axios'

// All requests go to /api — Vite proxy forwards to Spring Boot on port 8080
const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // include session cookie
})

export default api
