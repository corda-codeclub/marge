<template>
  <div id="app" v-bind:class="{loading: loading}">
    <img src="./assets/logo.png">
    <div class="org-name">{{state.name}}</div>
    <div><label>Balance: £</label><span>{{state.balance}}</span></div>
    <div v-cloak>
      <h2>Patients</h2>
      <ul>
        <li v-for="patient in state.patients">
          <div><span>{{patient.name}}</span></div>
        </li>
      </ul>
    </div>
    <ul></ul>
  </div>
</template>

<script>
  import {Proxy} from 'braid-client';

  export default {
    name: 'app',
    data: function () {
      return {
        state: {
          name: '',
          patients: [],
          balance: "0.00"
        },
        loading: true
      }
    },
    created() {
      const path = window.location.protocol + '//' + window.location.host + '/api/';
      console.log('connect to', path);
      this.proxy = new Proxy({url: path}, this.onOpen, this.onClose, this.onError);
      window.proxy = this.proxy; // for experimentation
    },
    methods: {
      onOpen() {
        console.log('braid connected', this.proxy);
        this.proxy.hospital.getInitialState()
          .then(state => {
            console.log(state.balance);
            this.state = state;
            this.loading = false
          })
          .catch(err => console.error("failed to initialise", err))
      },
      onClose() {
        console.log('braid closed');
      },
      onError(err) {
        console.error('connection error', err);
      },
    }
  }
</script>

<style>
  #app {
    font-family: 'Avenir', Helvetica, Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    color: #2c3e50;
    margin-top: 60px;
  }

  .org-name {
    font-size: 40px;
  }

  .loading > * { display:none; }
  .loading::before { content: "loading..."; }
</style>
