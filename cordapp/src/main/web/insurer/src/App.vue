<template>
  <div id="app">
    <img src="./assets/logo.png">
    <div>{{name}}</div>
  </div>
</template>

<script>
  import {Proxy} from 'braid-client';

  export default {
    name: 'app',
    data: function(){
      return {
        name: ''
      }
    },
    mounted() {
      const path = window.location.protocol + '//' + window.location.host + '/api/';
      console.log('connect to', path);
      this.proxy = new Proxy({url: path}, this.onOpen, this.onClose, this.onError);
      window.proxy = this.proxy; // for experimentation
    },
    methods: {
      getNodeName() {
        this.proxy.network.myNodeInfo()
          .then(nodeInfo => {
            this.name = nodeInfo.legalIdentities[0].name;
          });
      },
      onOpen() {
        console.log('braid connected', this.proxy);
        this.getNodeName()
      },
      onClose() {
        console.log('braid closed');
      },
      onError(err) {
        console.error('connection error', err);
      }
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
</style>
