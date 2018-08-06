// eslint-disable-next-line
<template>
  <v-app id="app" v-bind:class="{loading: loading}">
    <v-toolbar app>
      <div class="org-name">{{state.name}}</div>
    </v-toolbar>
    <v-navigation-drawer
        v-model="drawer"
        fixed
        app
    >
      <div class="display-2">Balance</div>
      <div class="display-1">£ {{state.balance}}</div>
      <v-divider></v-divider>
    </v-navigation-drawer>
    <v-content>
      <v-container fluid>
        <v-btn color="red lighten-2" dark @click="createTreatment">Create Treatments</v-btn>

        <div>
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

        <v-dialog
            v-model="treatmentDialog"
            width="800"
        >
          <v-card>
            <v-card-title
                class="headline grey lighten-2"
                primary-title
            >
              Create Treatment
            </v-card-title>
            <v-layout wrap>
              <v-flex xs12>
                <v-combobox
                    v-model="newTreatment.name"
                    :items="patientNames"
                    label="Patient"
                ></v-combobox>
              </v-flex>
              <v-flex xs12>
                <v-text-field
                    label="Treatment"
                    placeholder="Enter treatment description"
                >{{newTreatment.description}}
                </v-text-field>
              </v-flex>
            </v-layout>

            <v-divider></v-divider>

            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn
                  color="primary"
                  flat
                  @click="sendTreatment"
              >
                Create Treatment
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>
      </v-container>
    </v-content>
    <v-footer app></v-footer>
  </v-app>
</template>

<script>
  import {Proxy} from 'braid-client';

  export default {
    name: 'app',
    data: function () {
      return {
        treatmentDialog: false,
        newTreatment: {
          name: "",
          description: ""
        },
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
    computed: {
      patientNames: function () {
        return this.state.patients.map(patient => patient.name)
      }
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
      createTreatment() {
        this.treatmentDialog = true;
        console.log("create treatment");
      },
      sendTreatment() {
        this.treatmentDialog = false;
        console.log(this.newTreatment);
        this.proxy.hospital.processTreatmentRequest(this.newTreatment)
          .then(result => {
            console.log("treatment sent", result);
          }).catch(err => console.error("failed during sending of treatment", err));
      }
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

  .loading > * {
    display: none;
  }

  .loading::before {
    content: "loading...";
  }
</style>
