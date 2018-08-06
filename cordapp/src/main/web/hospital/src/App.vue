// eslint-disable-next-line
<template>
  <v-app id="app" v-bind:class="{loading: loading}">
    <v-toolbar app>
      <div class="org-name">{{state.name}} - Treatments</div>
    </v-toolbar>
    <v-navigation-drawer fixed app>
      <div class="display-2">Balance</div>
      <div class="display-1">£ {{balance}}</div>
      <v-divider></v-divider>
      <v-btn color="green lighten-1" dark @click="createTreatment">Create Treatment</v-btn>
    </v-navigation-drawer>
    <v-content>
      <v-container fluid>
        <v-card>
          <v-list three-line>
            <template v-for="(item, key) in treatments">
              <v-list-tile
                  :key="key"
                  avatar
                  @click=""
              >
                <v-list-tile-action>
                  <v-icon color="pink">credit_card</v-icon>
                </v-list-tile-action>

                <v-list-tile-content>
                  <v-list-tile-title>{{item.treatment.patient.name}}</v-list-tile-title>
                  <v-list-tile-sub-title>{{item.treatment.description}}</v-list-tile-sub-title>
                  <v-list-tile-sub-title>{{item.treatmentStatus}}</v-list-tile-sub-title>
                </v-list-tile-content>
              </v-list-tile>
              <v-list-tile>
                <v-list-tile-sub-title>£{{amountToString(item.estimatedTreatmentCost)}} covered up to
                  £{{amountToString(item.insurerQuote.maxCoveredValue)}} by
                  {{organisationFromParty(item.insurerQuote.insurer)}}
                </v-list-tile-sub-title>
              </v-list-tile>
              <v-list-tile v-if="item.treatmentStatus=='QUOTED'">
                <v-text-field v-model="actualAmount" label="Actual Amount"
                              placeholder="enter actual amount"></v-text-field>
                <v-btn color="blue lighten-1" dark @click="requestPayment(key)">Request Payment</v-btn>
              </v-list-tile>
              <v-divider></v-divider>
            </template>
          </v-list>
        </v-card>
      </v-container>
    </v-content>
    <v-footer app></v-footer>
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
                v-model="newTreatment.description"
            >
            </v-text-field>
          </v-flex>
          <v-flex xs12>
            <v-text-field
                v-model="newTreatment.amount"
                label="Estimated Amount"
            ></v-text-field>
          </v-flex>
        </v-layout>

        <v-divider></v-divider>

        <v-card-actions>
          <v-btn
              color="primary"
              flat
              @click="sendTreatment"
          >
            Create Treatment
          </v-btn>
          <v-spacer></v-spacer>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-app>
</template>

<script>
  import {Proxy} from 'braid-client';

  export default {
    name: 'app',
    data: function () {
      return {
        newStateChange: 1,
        treatmentDialog: false,
        newTreatment: {
          name: "",
          description: "",
          amount: ""
        },
        actualAmount: "0.00",
        state: {
          name: '',
          patients: [],
          balance: "0.00",
          treatments: {}
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
      },
      treatments: function () {
        if (this.newStateChange > 0) {
          return this.state.treatments;
        }
      },
      balance: function () {
        if (this.newStateChange > 0) {
          return this.state.balance;
        }
      }
    },
    methods: {
      onOpen() {
        console.log('braid connected', this.proxy);
        this.proxy.hospital.listenForTreatments(update => this.onTreatments(update), error => this.onTreatmentsError(error), () => {
        })
        this.proxy.hospital.getInitialState()
          .then(state => {
            console.log(state);
            this.state = state;
            this.loading = false;
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
        console.log("submitting treatment", this.newTreatment);
        this.proxy.hospital.processTreatmentRequest(this.newTreatment)
          .then(result => {
            console.log("treatment submitted and we received", result);
          })
          .catch(err => {
            console.error("failed during sending of treatment", err);
          });
        this.newTreatment = {
          name: "",
          description: "",
          amount: ""
        };
      },
      onTreatments(treatments) {
        treatments.forEach(treatment => {
          const key = treatment.linearId.id;
          this.state.treatments[key] = treatment;
          this.newStateChange += 1;
        });
        console.log(treatments);
        window.setTimeout(this.updateBalance, 500);
      },
      onTreatmentsError(error) {
        console.log(error);
      },
      requestPayment(id) {
        console.log("requesting payment for", id, this.actualAmount);
        const val = parseFloat(this.actualAmount) * 100;
        this.proxy.hospital.requestPayment(id, val)
          .then(result => {
            console.log("paid!"); // we'll get the notification later
          })
          .catch(err => {
            console.error("failed to be paid", err);
          });
      },
      updateBalance() {
        this.proxy.ledger.balanceForAccount('hospital')
          .then(result => {
            const balance = result[0].quantity / 100;
            this.state.balance = (result[0].quantity / 100).toFixed(2);
            this.newStateChange += 1;
            console.log("balance", result);
          })
          .catch(err => {
            console.error("failure to get balance", err);
          })
      },
      amountToString(amount) {
        return (amount.quantity / 100).toFixed(2);
      },
      organisationFromParty(party) {
        return this.parseX509Name(party.name).O;
      },
      parseX509Name(name) {
        return name.split(',')
          .map(it => it.trim())
          .map(it => it.split('='))
          .reduce((obj, pair) => {
            obj[pair[0]] = pair[1];
            return obj;
          }, {});
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
