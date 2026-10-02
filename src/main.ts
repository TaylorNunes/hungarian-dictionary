import { mount } from 'svelte';
import { registerSW } from 'virtual:pwa-register';
import App from './App.svelte';
import './app.css';

registerSW({ immediate: true });

const app = mount(App, { target: document.getElementById('app')! });

export default app;
