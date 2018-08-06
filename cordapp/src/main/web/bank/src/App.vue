<template>
  <div id="app">
    <img src="./assets/logo.png">
    <div class="org-name">{{state.name}}</div>
    <table>
      <thead>
      <tr><td>Account</td><td>Amount</td></tr>
      </thead>
      <tbody>
      <tr v-for="(balance, account) in state.balances">
        <td>{{account}}</td>
        <td>£{{balance}}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
  import {Proxy} from 'braid-client';

  export default {
    name: 'app',
    data: function () {
      return {
        state: {
          name: 'Init',
          balances: {
            "John": "100.00"
          }
        }
      }
    },
    mounted() {
      const path = window.location.protocol + '//' + window.location.host + '/api/';
      console.log('connect to', path);
      this.proxy = new Proxy({url: path}, this.onOpen, this.onClose, this.onError);
      window.proxy = this.proxy; // for experimentation
    },
    methods: {
      onOpen() {
        console.log('braid connected', this.proxy);
        this.proxy.bank.getInitialState()
          .then(state => {
            console.log("initial state", state);
            this.state = state;
          })
          .catch(err => {
            console.log("failed to get initial state", err);
          });
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
    text-align: center;
    color: #2c3e50;
    margin-top: 60px;
  }

  .org-name {
    font-size: 40px;
  }

  table {
    font-size: 40px;
  }
</style>
